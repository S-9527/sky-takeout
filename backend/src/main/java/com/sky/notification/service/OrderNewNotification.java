package com.sky.notification.service;

/**
 * {@code ORDER_NEW} 的载荷(契约 §4.3)。时间统一用 ISO-8601 带偏移的**字符串**:
 * 通知是跨进程的 JSON 载荷,不把 Java 时间类型的序列化细节漏出去。
 */
public record OrderNewNotification(
        Long orderId,
        String orderNo,
        long payAmountCents,
        String consignee,
        String phone,
        String detail,
        int itemCount,
        String remark,
        String placedAt,
        String paidAt
) {
}
