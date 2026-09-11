package com.sky.cart.domain;

import com.baomidou.mybatisplus.annotation.IEnum;

/**
 * 购物车/订单里的商品类型。数据库存 VARCHAR:DISH / SETMEAL。
 *
 * <p>刻意不在各上下文之间共享这个枚举:它是业务概念,不是共享内核;跨上下文共享业务类型
 * 会让两个上下文在编译期焊死(架构规则 L4)。值相同、各自定义,是限界上下文的正常代价。
 */
public enum ItemType implements IEnum<String> {

    DISH,

    SETMEAL;

    @Override
    public String getValue() {
        return name();
    }
}
