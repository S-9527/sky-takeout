package com.sky.order.enumeration;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 订单状态：1待付款 2待接单 3已接单 4派送中 5已完成 6已取消
 */
@Getter
@RequiredArgsConstructor
public enum OrderStatus {

    PENDING_PAYMENT(1, "待付款"),
    TO_BE_CONFIRMED(2, "待接单"),
    CONFIRMED(3, "已接单"),
    DELIVERY_IN_PROGRESS(4, "派送中"),
    COMPLETED(5, "已完成"),
    CANCELLED(6, "已取消");

    private final Integer code;
    private final String desc;

    public static OrderStatus fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (OrderStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }
}