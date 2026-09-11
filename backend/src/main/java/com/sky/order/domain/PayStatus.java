package com.sky.order.domain;

import com.baomidou.mybatisplus.annotation.IEnum;

/** 支付状态。订单上的冗余列(D6),便于列表查询;真相在 payment/refund 表。 */
public enum PayStatus implements IEnum<String> {

    UNPAID,
    PAID,
    REFUNDED,
    PARTIAL_REFUNDED;

    public boolean isPaid() {
        return this == PAID || this == PARTIAL_REFUNDED;
    }

    @Override
    public String getValue() {
        return name();
    }
}
