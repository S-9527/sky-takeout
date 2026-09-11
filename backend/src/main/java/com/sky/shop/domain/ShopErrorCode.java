package com.sky.shop.domain;

import org.springframework.http.HttpStatus;

import com.sky.common.error.ErrorCode;

/** 门店上下文错误码。取值与 {@code docs/03-api.md} 的 SHOP_* 表逐字一致。 */
public enum ShopErrorCode implements ErrorCode {

    /** 单行配置缺失:生产若只跑 V1(不灌种子),这张表就是空的。 */
    SHOP_STATUS_NOT_FOUND(HttpStatus.NOT_FOUND, "门店信息不存在"),

    /** 营业时间格式非法(HH:mm / HH:mm:ss 之外)或逻辑矛盾(开始不早于结束)。 */
    SHOP_BUSINESS_HOURS_INVALID(HttpStatus.BAD_REQUEST, "营业时间设置不合理");

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ShopErrorCode(HttpStatus httpStatus, String defaultMessage) {
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
