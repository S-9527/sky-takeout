package com.sky.payment.domain;

import com.baomidou.mybatisplus.annotation.IEnum;

/**
 * 退款原因分类。
 *
 * <p>取值以**库里的 CHECK 约束**为准(MERCHANT_REJECT / MERCHANT_CANCEL / CUSTOMER_APPLY / OTHER)。
 * openapi 的 RefundCreateRequest 原本写的是 {@code CUSTOMER_REQUEST},与库约束不一致——那样插库会直接
 * 违反 CHECK(500)。已按库与 02-database 的取值改成 {@code CUSTOMER_APPLY}。
 */
public enum RefundReasonType implements IEnum<String> {

    MERCHANT_REJECT,
    MERCHANT_CANCEL,
    CUSTOMER_APPLY,
    OTHER;

    @Override
    public String getValue() {
        return name();
    }
}
