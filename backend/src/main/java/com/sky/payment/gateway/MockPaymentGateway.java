package com.sky.payment.gateway;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

import com.sky.payment.domain.Payment;
import com.sky.payment.domain.PaymentChannel;

/**
 * 本地开发通道:不调微信,发起即成功。
 *
 * <p>只在 {@code sky.payment.gateway=mock}(默认)时装配;把配置改成 {@code wechat} 而没有真实实现时,
 * 应用会在启动阶段因为找不到 {@link PaymentGateway} 而失败——这是刻意的失败方向:
 * 宁可起不来,也不要"配置成真实支付、实际走模拟"。
 */
@Component
@ConditionalOnProperty(name = "sky.payment.gateway", havingValue = "mock", matchIfMissing = true)
public class MockPaymentGateway implements PaymentGateway {

    private static final String SIGN_TYPE = "RSA";

    @Override
    public PaymentChannel channel() {
        return PaymentChannel.MOCK;
    }

    @Override
    public boolean settlesImmediately() {
        return true;
    }

    @Override
    public Prepay prepay(Payment payment, String payerOpenid) {
        String prepayId = "mock_prepay_" + payment.getId() + "_" + UUID.randomUUID().toString().replace("-", "");
        return new Prepay(prepayId,
                String.valueOf(Instant.now().getEpochSecond()),
                UUID.randomUUID().toString().replace("-", ""),
                "prepay_id=" + prepayId,
                SIGN_TYPE,
                "mock-signature");
    }

    @Override
    public RefundReceipt refund(String refundNo, Payment payment, long amountCents, String reason) {
        return new RefundReceipt(true, "mock_refund_" + refundNo, "mock 通道受理成功");
    }
}
