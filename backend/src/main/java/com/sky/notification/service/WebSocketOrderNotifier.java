package com.sky.notification.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

import com.sky.common.util.Times;

/**
 * {@link OrderNotifier} 的 WebSocket 实现。
 *
 * <p>推送一律走"事务提交后":有事务时注册 {@code afterCommit} 回调,没有事务(催单不写库)时直接发。
 * 这是后端架构 §4.4 的要求——推送发出去了而事务回滚,是最难查的一类不一致。
 */
@Service
public class WebSocketOrderNotifier implements OrderNotifier {

    private final AdminNotificationService notifications;

    public WebSocketOrderNotifier(AdminNotificationService notifications) {
        this.notifications = notifications;
    }

    @Override
    public void orderNew(OrderNewNotification order) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", order.orderId());
        payload.put("orderNo", order.orderNo());
        payload.put("payAmountCents", order.payAmountCents());
        payload.put("consignee", order.consignee());
        payload.put("phone", order.phone());
        payload.put("detail", order.detail());
        payload.put("itemCount", order.itemCount());
        payload.put("remark", order.remark());
        payload.put("placedAt", order.placedAt());
        payload.put("paidAt", order.paidAt());
        afterCommit(() -> notifications.send("ORDER_NEW", payload));
    }

    @Override
    public void orderReminder(Long orderId, String orderNo, String status, String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", orderId);
        payload.put("orderNo", orderNo);
        payload.put("status", status);
        payload.put("message", message);
        payload.put("urgedAt", OffsetDateTime.now(Times.ZONE).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        afterCommit(() -> notifications.send("ORDER_URGE", payload));
    }

    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
