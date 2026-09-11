package com.sky.order.api.dto;

import java.time.OffsetDateTime;

import com.sky.common.util.Times;
import com.sky.payment.service.PaymentService;

/**
 * 订单详情里的支付记录,形状与 openapi 的 {@code Payment} 一致。
 *
 * <p>为什么在 order 上下文再包一层,而不是直接把 payment 的视图丢进响应:
 * 两个上下文的 DTO 各自演进,任何一方改字段都不该悄悄改变另一方的对外契约。
 * 这里只做字段搬运,跨上下文依赖的是 {@code payment.service} 包(L4 允许的方向)。
 */
public record PaymentSummaryResponse(
        Long id,
        Long orderId,
        String orderNo,
        String channel,
        String status,
        Long amountCents,
        String transactionId,
        String prepayId,
        OffsetDateTime paidAt,
        OffsetDateTime createdAt
) {

    public static PaymentSummaryResponse from(PaymentService.OrderPayment payment) {
        return new PaymentSummaryResponse(
                payment.id(),
                payment.orderId(),
                payment.orderNo(),
                payment.channel(),
                payment.status(),
                payment.amountCents(),
                payment.transactionId(),
                payment.prepayId(),
                Times.toOffset(payment.paidAt()),
                Times.toOffset(payment.createdAt()));
    }
}
