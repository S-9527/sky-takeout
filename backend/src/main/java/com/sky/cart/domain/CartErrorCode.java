package com.sky.cart.domain;

import org.springframework.http.HttpStatus;

import com.sky.common.error.ErrorCode;

/**
 * 购物车上下文错误码。取值与 {@code docs/03-api.md} 的 CART_* 表逐字一致。
 *
 * <p>{@code CART_ITEM_TYPE_MISMATCH} 已从契约中移除:请求形状是"按 itemType 只传对应的 id",
 * 类型矛盾在参数层就返回 400,那个码没有可达路径。
 */
public enum CartErrorCode implements ErrorCode {

    /** 不存在、或不属于当前顾客——刻意不区分,避免变成一个"这行存不存在"的探测器。 */
    CART_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "购物车项不存在"),

    CART_ITEM_OFF_SALE(HttpStatus.UNPROCESSABLE_ENTITY, "商品已下架,无法加入购物车"),
    CART_FLAVOR_REQUIRED(HttpStatus.UNPROCESSABLE_ENTITY, "请选择商品口味"),
    CART_FLAVOR_INVALID(HttpStatus.UNPROCESSABLE_ENTITY, "所选口味已变更,请重新选择"),
    CART_QUANTITY_INVALID(HttpStatus.UNPROCESSABLE_ENTITY, "数量不合理,请重新输入");

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    CartErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    @Override
    public String code() {
        return name();
    }

    @Override
    public HttpStatus httpStatus() {
        return httpStatus;
    }

    @Override
    public String defaultMessage() {
        return defaultMessage;
    }
}
