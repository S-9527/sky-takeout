package com.sky.order.api.dto;

import java.time.OffsetDateTime;

import com.sky.common.util.Times;
import com.sky.payment.service.PaymentService;

/**
 * 订单详情里的退款记录,形状与 openapi 的 {@code Refund} 一致。
 *
 * <p>{@code reasonType} 在 payment 侧是枚举,这里已经转成字符串 ——
 * 契约里它就是字符串,前端按值展示,不需要认识后端的枚举。
 */
public record RefundSummaryResponse(
        Long id,
        Long paymentId,
        Long orderId,
        String orderNo,
        String refundNo,
        Long amountCents,
        String status,
        String reason,
        String reasonType,
        OffsetDateTime refundedAt,
        OffsetDateTime createdAt
) {

    public static RefundSummaryResponse from(PaymentService.OrderRefund refund) {
        return new RefundSummaryResponse(
                refund.id(),
                refund.paymentId(),
                refund.orderId(),
                refund.orderNo(),
                refund.refundNo(),
                refund.amountCents(),
                refund.status(),
                refund.reason(),
                refund.reasonType(),
                Times.toOffset(refund.refundedAt()),
                Times.toOffset(refund.createdAt()));
    }
}
