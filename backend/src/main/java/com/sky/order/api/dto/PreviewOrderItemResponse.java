package com.sky.order.api.dto;

import java.util.List;

import com.sky.cart.service.CartService;
import com.sky.order.service.OrderService;

/** 试算明细(实时值,非快照)。对应 openapi 的 {@code PreviewOrderItem}。 */
public record PreviewOrderItemResponse(
        String itemType,
        Long dishId,
        Long setmealId,
        String name,
        String imageUrl,
        Long unitPriceCents,
        Integer quantity,
        Long amountCents,
        List<CartService.FlavorChoiceRef> flavorChoice
) {

    public static PreviewOrderItemResponse from(OrderService.PreviewLine line) {
        return new PreviewOrderItemResponse(line.itemType(), line.dishId(), line.setmealId(), line.name(),
                line.imageUrl(), line.unitPriceCents(), line.quantity(), line.amountCents(), line.flavorChoice());
    }
}
