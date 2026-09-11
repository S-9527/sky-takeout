package com.sky.cart.api.dto;

import java.util.List;

import com.sky.cart.service.CartService;

/** 购物车全量视图。对应 openapi 的 {@code CartView}。 */
public record CartViewResponse(
        List<CartCategoryGroupResponse> groups,
        int totalQuantity,
        long totalAmountCents
) {

    public static CartViewResponse from(CartService.CartSummary summary) {
        return new CartViewResponse(
                summary.groups().stream().map(CartCategoryGroupResponse::from).toList(),
                summary.totalQuantity(),
                summary.totalAmountCents());
    }
}
