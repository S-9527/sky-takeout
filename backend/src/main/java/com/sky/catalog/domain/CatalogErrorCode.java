package com.sky.catalog.domain;

import org.springframework.http.HttpStatus;

import com.sky.common.error.ErrorCode;

/** 商品上下文错误码。取值与 {@code docs/03-api.md} 的 CATEGORY_* / DISH_* / SETMEAL_* 表逐字一致。 */
public enum CatalogErrorCode implements ErrorCode {

    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "分类不存在"),
    CATEGORY_NAME_TAKEN(HttpStatus.CONFLICT, "同类型下已存在同名分类"),
    CATEGORY_IN_USE(HttpStatus.UNPROCESSABLE_ENTITY, "该分类下仍有商品,无法删除"),
    CATEGORY_TYPE_IMMUTABLE(HttpStatus.UNPROCESSABLE_ENTITY, "分类类型不可修改"),
    CATEGORY_DISABLED(HttpStatus.UNPROCESSABLE_ENTITY, "分类已禁用,无法添加商品"),

    DISH_NOT_FOUND(HttpStatus.NOT_FOUND, "菜品不存在"),
    DISH_NAME_TAKEN(HttpStatus.CONFLICT, "该分类下已存在同名菜品"),
    DISH_CATEGORY_TYPE_MISMATCH(HttpStatus.UNPROCESSABLE_ENTITY, "菜品只能归属于菜品分类"),
    DISH_OFF_SALE(HttpStatus.UNPROCESSABLE_ENTITY, "商品已下架,请重新选择"),

    /** 一个条件一个错误码:删除被套餐引用的菜品(镜像条件是 DISH_IN_USE_BY_SETMEAL,已按 openapi 收敛掉)。 */
    SETMEAL_CONTAINS_DISH(HttpStatus.UNPROCESSABLE_ENTITY, "菜品已被套餐引用,请先从套餐移除"),

    SETMEAL_NOT_FOUND(HttpStatus.NOT_FOUND, "套餐不存在"),
    SETMEAL_NAME_TAKEN(HttpStatus.CONFLICT, "该分类下已存在同名套餐"),
    SETMEAL_CATEGORY_TYPE_MISMATCH(HttpStatus.UNPROCESSABLE_ENTITY, "套餐只能归属于套餐分类"),
    SETMEAL_ITEMS_EMPTY(HttpStatus.UNPROCESSABLE_ENTITY, "套餐必须至少包含一个菜品"),
    SETMEAL_ITEMS_DUPLICATED(HttpStatus.CONFLICT, "同一菜品不能重复添加"),
    SETMEAL_PRICE_EXCEEDS_ITEMS(HttpStatus.UNPROCESSABLE_ENTITY, "套餐定价不能高于所含菜品合计"),
    SETMEAL_DISH_NOT_ON_SALE(HttpStatus.UNPROCESSABLE_ENTITY, "所含菜品中有停售商品,无法起售"),
    SETMEAL_OFF_SALE(HttpStatus.UNPROCESSABLE_ENTITY, "套餐已下架,请重新选择");

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    CatalogErrorCode(HttpStatus httpStatus, String defaultMessage) {
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
