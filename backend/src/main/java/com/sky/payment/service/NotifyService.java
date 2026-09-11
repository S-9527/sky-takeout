package com.sky.payment.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import com.sky.common.error.BusinessException;
import com.sky.common.util.Json;
import com.sky.payment.domain.Payment;
import com.sky.payment.domain.PaymentErrorCode;
import com.sky.payment.domain.PaymentStatus;
import com.sky.payment.domain.Refund;
import com.sky.payment.gateway.WechatNotifyCodec;
import com.sky.payment.mapper.PaymentMapper;
import com.sky.payment.mapper.RefundMapper;

/**
 * 支付/退款回调的处理。
 *
 * <p>幂等(R7)最终落在 {@link PaymentService#applyPaySuccess} 与
 * {@link PaymentService#applyRefundSuccess}:它们先判"是否已经生效",再比对
 * {@code transaction_id} / 退款单状态,所以同一笔通知重复送达只生效一次。
 *
 * <p>报文解析失败与签名失败是**回调方的问题**(400,微信不该重试);订单/退款查不到、
 * 金额不符则按业务错误上报(由控制器决定应答码)。
 */
@Service
public class NotifyService {

    private static final String EVENT_TRANSACTION_SUCCESS = "TRANSACTION.SUCCESS";
    private static final String EVENT_REFUND_SUCCESS = "REFUND.SUCCESS";
    private static final String REFUND_STATUS_SUCCESS = "SUCCESS";

    private final WechatNotifyCodec notifyCodec;
    private final PaymentMapper paymentMapper;
    private final RefundMapper refundMapper;
    private final PaymentService paymentService;

    public NotifyService(WechatNotifyCodec notifyCodec, PaymentMapper paymentMapper, RefundMapper refundMapper,
                         PaymentService paymentService) {
        this.notifyCodec = notifyCodec;
        this.paymentMapper = paymentMapper;
        this.refundMapper = refundMapper;
        this.paymentService = paymentService;
    }

    /**
     * 处理支付结果回调。
     *
     * @return true 表示这次真的生效了;false 表示重复通知(同样应答成功,让微信停止重试)
     */
    @Transactional
    public boolean handlePayNotify(String signature, String timestamp, String nonce, String rawBody,
                                   String eventType, String ciphertext, String algorithm,
                                   String resourceNonce, String associatedData) {
        notifyCodec.verifySignature(signature, timestamp, nonce, rawBody);
        if (eventType != null && !EVENT_TRANSACTION_SUCCESS.equals(eventType)) {
            // 非成功事件(如关单)当前不改变订单状态,应答成功即可
            return false;
        }

        WechatNotifyCodec.DecryptedNotify decrypted = decrypt(ciphertext, algorithm, resourceNonce, associatedData);
        Payment payment = findPayablePayment(decrypted.outTradeNo());
        if (decrypted.amountCents() != null && !decrypted.amountCents().equals(payment.getAmountCents())) {
            // 回调金额与订单实付不一致:不落库,交给人工核对(R5:金额只认服务端)
            throw new BusinessException(PaymentErrorCode.PAY_AMOUNT_MISMATCH,
                    "支付金额与订单不符,请刷新后重试",
                    List.of(new com.sky.common.error.ErrorResponse.Detail("amountCents",
                            "订单实付为 " + payment.getAmountCents() + " 分")));
        }
        return paymentService.applyPaySuccess(payment.getId(), decrypted.transactionId(), rawBody);
    }

    /**
     * 处理退款结果回调:成功置 SUCCESS,异常/关闭置 FAILED。
     *
     * @return true 表示这次真的生效了
     */
    @Transactional
    public boolean handleRefundNotify(String signature, String timestamp, String nonce, String rawBody,
                                      String eventType, String ciphertext, String algorithm,
                                      String resourceNonce, String associatedData) {
        notifyCodec.verifySignature(signature, timestamp, nonce, rawBody);
        WechatNotifyCodec.DecryptedNotify decrypted = decrypt(ciphertext, algorithm, resourceNonce, associatedData);

        Refund refund = refundMapper.selectOne(com.baomidou.mybatisplus.core.toolkit.Wrappers.<Refund>lambdaQuery()
                .eq(Refund::getRefundNo, decrypted.outRefundNo())
                .last("LIMIT 1"));
        if (refund == null) {
            throw new BusinessException(PaymentErrorCode.PAY_REFUND_NOT_FOUND);
        }

        boolean success = EVENT_REFUND_SUCCESS.equals(eventType)
                || REFUND_STATUS_SUCCESS.equals(decrypted.refundStatus());
        if (success) {
            return paymentService.applyRefundSuccess(refund.getId(), rawBody);
        }
        return paymentService.applyRefundFailure(refund.getId(), rawBody);
    }

    private WechatNotifyCodec.DecryptedNotify decrypt(String ciphertext, String algorithm, String nonce,
                                                      String associatedData) {
        String plaintext = notifyCodec.decrypt(algorithm, ciphertext, nonce, associatedData);
        try {
            Map<String, Object> body = Json.readMap(plaintext);
            return new WechatNotifyCodec.DecryptedNotify(
                    asString(body.get("out_trade_no")),
                    asString(body.get("transaction_id")),
                    asLong(body.get("amount")),
                    asString(body.get("trade_state")),
                    asString(body.get("out_refund_no")),
                    asString(body.get("refund_status")));
        } catch (RuntimeException ex) {
            // 明文不是合法 JSON(或结构不对)→ 报文不可读,不落库
            throw new BusinessException(PaymentErrorCode.PAY_NOTIFY_DECRYPT_FAILED);
        }
    }

    private Payment findPayablePayment(String outTradeNo) {
        if (outTradeNo == null || outTradeNo.isBlank()) {
            throw new BusinessException(PaymentErrorCode.PAY_ORDER_NOT_FOUND);
        }
        Payment payment = paymentMapper.selectOne(com.baomidou.mybatisplus.core.toolkit.Wrappers.<Payment>lambdaQuery()
                .eq(Payment::getOrderNo, outTradeNo)
                .ne(Payment::getStatus, PaymentStatus.CLOSED)
                .orderByDesc(Payment::getId)
                .last("LIMIT 1"));
        if (payment == null) {
            throw new BusinessException(PaymentErrorCode.PAY_ORDER_NOT_FOUND);
        }
        return payment;
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /** 微信回调的金额是 {@code {"total": 10400}} 这样的对象;也容忍直接给数字。 */
    @SuppressWarnings("unchecked")
    private static Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof Map<?, ?> map) {
            Object total = ((Map<String, Object>) map).get("total");
            return total == null ? null : asLong(total);
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
