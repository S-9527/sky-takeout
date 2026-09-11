package com.sky.payment.api.dto;

import java.time.OffsetDateTime;

import com.sky.common.util.Times;
import com.sky.payment.service.PaymentService;

/** 对外的支付记录。对应 openapi 的 {@code Payment}。 */
public record PaymentResponse(
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

    public static PaymentResponse from(PaymentService.PaymentView payment) {
        return new PaymentResponse(payment.id(), payment.orderId(), payment.orderNo(), payment.channel(),
                payment.status(), payment.amountCents(), payment.transactionId(), payment.prepayId(),
                Times.toOffset(payment.paidAt()), Times.toOffset(payment.createdAt()));
    }
}
