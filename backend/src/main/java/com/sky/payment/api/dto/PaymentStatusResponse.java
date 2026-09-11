package com.sky.payment.api.dto;

import com.sky.payment.service.PaymentService;

/** 顾客支付结果轮询视图。对应 openapi 的 {@code PaymentStatusView}。 */
public record PaymentStatusResponse(
        Long orderId,
        String orderNo,
        String orderStatus,
        String payStatus,
        PaymentResponse payment
) {

    public static PaymentStatusResponse from(PaymentService.StatusView view) {
        return new PaymentStatusResponse(view.orderId(), view.orderNo(), view.orderStatus(), view.payStatus(),
                view.payment() == null ? null : PaymentResponse.from(view.payment()));
    }
}
