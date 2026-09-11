package com.sky.order.api.dto;

import com.sky.order.service.OrderService;

/** 金额构成(单位:分)。对应 openapi 的 {@code OrderAmounts}。 */
public record AmountsResponse(
        Long totalAmountCents,
        Long packAmountCents,
        Long deliveryAmountCents,
        Long discountAmountCents,
        Long payAmountCents
) {

    public static AmountsResponse from(OrderService.Amounts amounts) {
        return new AmountsResponse(
                amounts.totalAmountCents(),
                amounts.packAmountCents(),
                amounts.deliveryAmountCents(),
                amounts.discountAmountCents(),
                amounts.payAmountCents());
    }
}
