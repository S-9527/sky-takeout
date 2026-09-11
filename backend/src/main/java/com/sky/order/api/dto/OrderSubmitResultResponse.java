package com.sky.order.api.dto;

import com.sky.order.service.OrderService;

/** 下单结果(轻量投影)。对应 openapi 的 {@code OrderSubmitResult}。 */
public record OrderSubmitResultResponse(
        Long id,
        String orderNo,
        String status,
        Long payAmountCents,
        boolean needPay
) {

    public static OrderSubmitResultResponse from(OrderService.SubmitResult result) {
        return new OrderSubmitResultResponse(
                result.id(), result.orderNo(), result.status().name(), result.payAmountCents(), result.needPay());
    }
}
