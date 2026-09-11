package com.sky.payment.api.dto;

import java.time.OffsetDateTime;

import com.sky.common.util.Times;
import com.sky.payment.service.PaymentService;

/** 对外的退款记录。对应 openapi 的 {@code Refund}。 */
public record RefundResponse(
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

    public static RefundResponse from(PaymentService.RefundView view) {
        var refund = view.refund();
        return new RefundResponse(refund.getId(), refund.getPaymentId(), refund.getOrderId(), view.orderNo(),
                refund.getRefundNo(), refund.getAmountCents(),
                refund.getStatus() == null ? null : refund.getStatus().name(),
                refund.getReason(),
                refund.getReasonType() == null ? null : refund.getReasonType().name(),
                Times.toOffset(refund.getRefundedAt()), Times.toOffset(refund.getCreatedAt()));
    }
}
