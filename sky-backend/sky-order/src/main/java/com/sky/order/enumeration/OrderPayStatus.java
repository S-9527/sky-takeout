package com.sky.order.enumeration;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 订单支付状态：0未支付 1已支付 2退款
 */
@Getter
@RequiredArgsConstructor
public enum OrderPayStatus {

    UN_PAID(0, "未支付"),
    PAID(1, "已支付"),
    REFUND(2, "退款");

    private final Integer code;
    private final String desc;
}