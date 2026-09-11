package com.sky.order.domain;

import com.baomidou.mybatisplus.annotation.IEnum;

/** 取消方。未取消时为 null。 */
public enum CancelSide implements IEnum<String> {

    CUSTOMER,
    MERCHANT,
    SYSTEM;

    @Override
    public String getValue() {
        return name();
    }
}
