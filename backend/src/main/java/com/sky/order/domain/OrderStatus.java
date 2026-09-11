package com.sky.order.domain;

import com.baomidou.mybatisplus.annotation.IEnum;

/**
 * 订单状态(领域文档 §4 状态机)。数据库存 VARCHAR。
 *
 * <p>合法迁移只在 {@link OrderStateMachine} 里判定,DB 不做约束——状态图必须只有一份实现。
 */
public enum OrderStatus implements IEnum<String> {

    PENDING_PAYMENT,
    PENDING_ACCEPTANCE,
    ACCEPTED,
    DELIVERING,
    COMPLETED,
    CANCELLED;

    /** 终态不可再迁移。 */
    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED;
    }

    /** 尚未支付(只有待付款订单允许发起支付;超时关单也只会关它)。 */
    public boolean isUnpaid() {
        return this == PENDING_PAYMENT;
    }

    @Override
    public String getValue() {
        return name();
    }
}
