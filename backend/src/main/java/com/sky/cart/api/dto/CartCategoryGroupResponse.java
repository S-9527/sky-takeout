package com.sky.cart.api.dto;

import java.util.List;

import com.sky.cart.service.CartService;

/** 购物车按分类分组。对应 openapi 的 {@code CartCategoryGroup}。 */
public record CartCategoryGroupResponse(
        Long categoryId,
        String categoryName,
        List<CartItemViewResponse> items
) {

    public static CartCategoryGroupResponse from(CartService.CartGroup group) {
        return new CartCategoryGroupResponse(
                group.categoryId(),
                group.categoryName(),
                group.items().stream().map(CartItemViewResponse::from).toList());
    }
}
