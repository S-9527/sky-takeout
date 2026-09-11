package com.sky.order.domain;

import com.baomidou.mybatisplus.annotation.IEnum;

/**
 * 订单明细的商品类型。
 *
 * <p>与购物车各自定义一份:业务枚举不跨上下文共享(架构规则 L4)。值相同、类型不同,
 * 是限界上下文的正常代价。
 */
public enum ItemType implements IEnum<String> {

    DISH,
    SETMEAL;

    @Override
    public String getValue() {
        return name();
    }
}
