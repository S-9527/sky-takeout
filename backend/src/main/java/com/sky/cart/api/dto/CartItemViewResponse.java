package com.sky.cart.api.dto;

import java.util.List;

import com.sky.cart.domain.FlavorChoice;
import com.sky.cart.service.CartService;

/**
 * 对外的购物车行。对应 openapi 的 {@code CartItemView}。
 *
 * <p>名称/图片/单价都是**实时值**(D4),前端不要把它们当成下单价格——下单时服务端会按库里价格重算(R5)。
 */
public record CartItemViewResponse(
        Long id,
        String itemType,
        Long dishId,
        Long setmealId,
        String name,
        String imageUrl,
        Long unitPriceCents,
        Integer quantity,
        Long amountCents,
        List<FlavorChoice> flavorChoice,
        boolean available,
        String unavailableReason
) {

    public static CartItemViewResponse from(CartService.CartItemSnapshot item) {
        return new CartItemViewResponse(
                item.id(),
                item.itemType() == null ? null : item.itemType().name(),
                item.dishId(),
                item.setmealId(),
                item.name(),
                item.imageUrl(),
                item.unitPriceCents(),
                item.quantity(),
                item.amountCents(),
                item.flavorChoice(),
                item.available(),
                item.unavailableReason());
    }
}
