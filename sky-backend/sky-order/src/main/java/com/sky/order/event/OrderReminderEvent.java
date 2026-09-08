package com.sky.order.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 客户催单领域事件。
 */
@Getter
@RequiredArgsConstructor
public class OrderReminderEvent {

    private final Long orderId;
    private final String orderNumber;
}