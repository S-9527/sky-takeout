package com.sky.payment.service;

import org.junit.jupiter.api.Test;


import com.sky.common.error.BusinessException;
import com.sky.payment.domain.Payment;
import com.sky.payment.domain.PaymentChannel;
import com.sky.payment.domain.PaymentErrorCode;
import com.sky.payment.domain.PaymentStatus;
import com.sky.payment.domain.Refund;
import com.sky.payment.domain.RefundStatus;
import com.sky.payment.gateway.MockWechatNotifyCodec;
import com.sky.payment.gateway.WechatNotifyCodec;
import com.sky.payment.mapper.PaymentMapper;
import com.sky.payment.mapper.RefundMapper;
import com.sky.testsupport.TableInfoTestSupport;
import org.junit.jupiter.api.BeforeAll;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotifyServiceTest {

    private static final String ORDER_NO = "202501011200000001";
    private static final String SIGNATURE = MockWechatNotifyCodec.MOCK_SIGNATURE;

    private final PaymentMapper paymentMapper = mock(PaymentMapper.class);
    private final RefundMapper refundMapper = mock(RefundMapper.class);
    private final PaymentService paymentService = mock(PaymentService.class);
    private final WechatNotifyCodec codec = new MockWechatNotifyCodec();

    private final NotifyService notifyService =
            new NotifyService(codec, paymentMapper, refundMapper, paymentService);

    @BeforeAll
    static void registerTableInfo() {
        TableInfoTestSupport.register(Payment.class, Refund.class);
    }

    private static Payment payment() {
        Payment payment = new Payment();
        payment.setId(6001L);
        payment.setOrderId(4001L);
        payment.setOrderNo(ORDER_NO);
        payment.setChannel(PaymentChannel.MOCK);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setAmountCents(5200L);
        return payment;
    }

    private static String payPlaintext(long total) {
        return "{\"out_trade_no\":\"" + ORDER_NO + "\",\"transaction_id\":\"TXN-1\","
                + "\"trade_state\":\"SUCCESS\",\"amount\":{\"total\":" + total + "}}";
    }

    // ---------------------------------------------------------------- 支付回调

    private boolean pay(String signature, String eventType, String ciphertext) {
        return notifyService.handlePayNotify(signature, "1735704070", "nonce", "{\"raw\":true}",
                eventType, ciphertext, "AEAD_AES_256_GCM", "resnonce", "transaction");
    }

    @Test
    void payNotifyAppliesSuccessOnce() {
        when(paymentMapper.selectOne(any())).thenReturn(payment());
        when(paymentService.applyPaySuccess(6001L, "TXN-1", "{\"raw\":true}")).thenReturn(true);

        assertThat(pay(SIGNATURE, "TRANSACTION.SUCCESS", payPlaintext(5200))).isTrue();
        verify(paymentService).applyPaySuccess(6001L, "TXN-1", "{\"raw\":true}");
    }

    /** 重复通知同样返回成功(让微信停止重试),但不再生效。 */
    @Test
    void duplicatePayNotifyIsAcknowledgedWithoutEffect() {
        when(paymentMapper.selectOne(any())).thenReturn(payment());
        when(paymentService.applyPaySuccess(any(), any(), any())).thenReturn(false);

        assertThat(pay(SIGNATURE, "TRANSACTION.SUCCESS", payPlaintext(5200))).isFalse();
    }

    @Test
    void badSignatureIsRejectedBeforeAnythingElse() {
        assertThatThrownBy(() -> pay("wrong-signature", "TRANSACTION.SUCCESS", payPlaintext(5200)))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(PaymentErrorCode.PAY_NOTIFY_SIGNATURE_INVALID));
        verify(paymentMapper, never()).selectOne(any());
        verify(paymentService, never()).applyPaySuccess(any(), any(), any());
    }

    @Test
    void nonSuccessEventIsIgnored() {
        assertThat(pay(SIGNATURE, "TRANSACTION.CLOSED", payPlaintext(5200))).isFalse();
        verify(paymentService, never()).applyPaySuccess(any(), any(), any());
    }

    @Test
    void unknownOrderNumberIsReported() {
        when(paymentMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> pay(SIGNATURE, "TRANSACTION.SUCCESS", payPlaintext(5200)))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(PaymentErrorCode.PAY_ORDER_NOT_FOUND));
    }

    /** R5:回调里的金额不可信,和订单实付不一致就不落库。 */
    @Test
    void amountMismatchIsRejected() {
        when(paymentMapper.selectOne(any())).thenReturn(payment());

        assertThatThrownBy(() -> pay(SIGNATURE, "TRANSACTION.SUCCESS", payPlaintext(1)))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.errorCode()).isEqualTo(PaymentErrorCode.PAY_AMOUNT_MISMATCH);
                    assertThat(ex.details()).isNotEmpty();
                });
        verify(paymentService, never()).applyPaySuccess(any(), any(), any());
    }

    @Test
    void unreadablePlaintextIsReportedAsDecryptFailure() {
        assertThatThrownBy(() -> pay(SIGNATURE, "TRANSACTION.SUCCESS", "这不是 JSON"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(PaymentErrorCode.PAY_NOTIFY_DECRYPT_FAILED));
    }

    // ---------------------------------------------------------------- 退款回调

    private static Refund refund(RefundStatus status) {
        Refund refund = new Refund();
        refund.setId(7001L);
        refund.setOrderId(4001L);
        refund.setRefundNo("RF202501011200000001");
        refund.setAmountCents(5200L);
        refund.setStatus(status);
        return refund;
    }

    private static String refundPlaintext(String refundStatus) {
        return "{\"out_refund_no\":\"RF202501011200000001\",\"refund_status\":\"" + refundStatus + "\"}";
    }

    private boolean refundNotify(String eventType, String plaintext) {
        return notifyService.handleRefundNotify(SIGNATURE, "1735704070", "nonce", "{\"raw\":true}",
                eventType, plaintext, "AEAD_AES_256_GCM", "resnonce", "refund");
    }

    @Test
    void refundSuccessMarksRefundSucceeded() {
        when(refundMapper.selectOne(any())).thenReturn(refund(RefundStatus.PENDING));
        when(paymentService.applyRefundSuccess(7001L, "{\"raw\":true}")).thenReturn(true);

        assertThat(refundNotify("REFUND.SUCCESS", refundPlaintext("SUCCESS"))).isTrue();
        verify(paymentService).applyRefundSuccess(7001L, "{\"raw\":true}");
    }

    /** 异常/关闭回调把退款置 FAILED,订单 payStatus 不动(钱没退出去)。 */
    @Test
    void refundAbnormalMarksRefundFailed() {
        when(refundMapper.selectOne(any())).thenReturn(refund(RefundStatus.PENDING));
        when(paymentService.applyRefundFailure(7001L, "{\"raw\":true}")).thenReturn(true);

        assertThat(refundNotify("REFUND.ABNORMAL", refundPlaintext("ABNORMAL"))).isTrue();
        verify(paymentService).applyRefundFailure(7001L, "{\"raw\":true}");
    }

    @Test
    void unknownRefundNumberIsReported() {
        when(refundMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> refundNotify("REFUND.SUCCESS", refundPlaintext("SUCCESS")))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(PaymentErrorCode.PAY_REFUND_NOT_FOUND));
    }

    @Test
    void refundNotifyAlsoVerifiesSignature() {
        assertThatThrownBy(() -> notifyService.handleRefundNotify("bad", "1", "n", "{}",
                "REFUND.SUCCESS", refundPlaintext("SUCCESS"), "AEAD_AES_256_GCM", "n", "refund"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(PaymentErrorCode.PAY_NOTIFY_SIGNATURE_INVALID));
    }
}
