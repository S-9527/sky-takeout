package com.sky.cart.api.dto;

import java.util.List;

import com.sky.cart.domain.FlavorChoice;
import com.sky.cart.domain.ItemType;

/**
 * 加入购物车。对应 openapi 的 {@code CartItemAddRequest}。
 *
 * <p>{@code quantity} 的范围与口味合法性都由 service 判定:契约给的是 422
 * {@code CART_QUANTITY_INVALID} / {@code CART_FLAVOR_*} 这些业务错误码,套 Bean Validation 会把它们
 * 统一成 400 {@code COMMON_VALIDATION_FAILED}。
 */
public record CartItemAddRequest(
        ItemType itemType,

        Long dishId,

        Long setmealId,

        Integer quantity,

        List<FlavorChoice> flavorChoice
) {
}
