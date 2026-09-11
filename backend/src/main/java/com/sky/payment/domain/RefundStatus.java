package com.sky.payment.domain;

import com.baomidou.mybatisplus.annotation.IEnum;

/** 退款流水状态。 */
public enum RefundStatus implements IEnum<String> {

    PENDING,
    SUCCESS,
    FAILED;

    public boolean isOpenOrSucceeded() {
        return this == PENDING || this == SUCCESS;
    }

    @Override
    public String getValue() {
        return name();
    }
}
