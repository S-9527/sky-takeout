package com.sky.order.api.dto;

import java.util.Map;

import com.sky.order.domain.OrderStatus;

/** 各状态订单数量。对应 openapi 的 {@code OrderStatusCounts}(未出现的状态补 0)。 */
public record OrderStatusCountsResponse(
        long all,
        long pendingPayment,
        long pendingAcceptance,
        long accepted,
        long delivering,
        long completed,
        long cancelled
) {

    public static OrderStatusCountsResponse from(Map<OrderStatus, Long> counts) {
        long all = counts.values().stream().mapToLong(Long::longValue).sum();
        return new OrderStatusCountsResponse(all,
                counts.getOrDefault(OrderStatus.PENDING_PAYMENT, 0L),
                counts.getOrDefault(OrderStatus.PENDING_ACCEPTANCE, 0L),
                counts.getOrDefault(OrderStatus.ACCEPTED, 0L),
                counts.getOrDefault(OrderStatus.DELIVERING, 0L),
                counts.getOrDefault(OrderStatus.COMPLETED, 0L),
                counts.getOrDefault(OrderStatus.CANCELLED, 0L));
    }
}
