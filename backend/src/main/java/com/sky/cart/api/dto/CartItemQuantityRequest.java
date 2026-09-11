package com.sky.cart.api.dto;

/** 覆盖式改量。对应 openapi 的 {@code CartItemQuantityRequest};{@code 0} 表示删除该行。 */
public record CartItemQuantityRequest(Integer quantity) {
}
