package com.sky.payment.domain;

import com.baomidou.mybatisplus.annotation.IEnum;

/** 支付流水状态。 */
public enum PaymentStatus implements IEnum<String> {

    PENDING,
    SUCCESS,
    FAILED,
    CLOSED;

    @Override
    public String getValue() {
        return name();
    }
}
