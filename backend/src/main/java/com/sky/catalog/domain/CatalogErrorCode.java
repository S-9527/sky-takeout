package com.sky.catalog.domain;

import org.springframework.http.HttpStatus;

import com.sky.common.error.ErrorCode;

/** 商品上下文错误码。取值与 {@code docs/03-api.md} 的 CATEGORY_* / DISH_* / SETMEAL_* 表逐字一致。 */
public enum CatalogErrorCode implements ErrorCode {

    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "分类不存在"),
    CATEGORY_NAME_TAKEN(HttpStatus.CONFLICT, "同类型下已存在同名分类"),
    CATEGORY_IN_USE(HttpStatus.UNPROCESSABLE_ENTITY, "该分类下仍有商品,无法删除"),
    CATEGORY_TYPE_IMMUTABLE(HttpStatus.UNPROCESSABLE_ENTITY, "分类类型不可修改"),
    CATEGORY_DISABLED(HttpStatus.UNPROCESSABLE_ENTITY, "分类已禁用,无法添加商品");

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
