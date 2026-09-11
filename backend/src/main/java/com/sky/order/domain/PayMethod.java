package com.sky.order.domain;

import com.baomidou.mybatisplus.annotation.IEnum;

/** 支付方式。未支付时为 null(数据库列可空)。 */
public enum PayMethod implements IEnum<String> {

    WECHAT,
    MOCK;

    @Override
    public String getValue() {
        return name();
    }
}
