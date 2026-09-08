package com.sky.order.event;

import com.sky.order.enumeration.OrderStatus;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 订单状态变更领域事件：状态机每次成功迁移后发布。
 */
@Getter
@RequiredArgsConstructor
public class OrderStatusChangedEvent {

    private final Long orderId;
    private final String orderNumber;
    private final OrderStatus from;
    private final OrderStatus to;
    private final String cause;
}