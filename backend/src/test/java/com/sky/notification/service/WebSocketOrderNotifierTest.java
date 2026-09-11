package com.sky.notification.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class WebSocketOrderNotifierTest {

    private final AdminNotificationService notifications = mock(AdminNotificationService.class);
    private final WebSocketOrderNotifier notifier = new WebSocketOrderNotifier(notifications);

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private static OrderNewNotification order() {
        return new OrderNewNotification(4001L, "202501011200000001", 5200L, "张三", "13800138000",
                "望京 1 号", 2, "不要香菜", "2025-01-01T12:00:00+08:00", "2025-01-01T12:00:05+08:00");
    }

    /** 催单不写库、没有事务:直接发。 */
    @Test
    void reminderWithoutTransactionSendsImmediately() {
        notifier.orderReminder(4001L, "202501011200000001", "ACCEPTED", "请尽快派送");

        verify(notifications).send(eq("ORDER_URGE"), any());
    }

    /** 有事务时必须等提交后才发:事务回滚了商家不该听到提示音。 */
    @Test
    void pushIsDeferredUntilAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();

        notifier.orderNew(order());

        verify(notifications, never()).send(any(), any());

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);

        verify(notifications).send(eq("ORDER_NEW"), any());
    }

    @Test
    void envelopeCarriesTypeMessageIdTimestampAndPayload() {
        String envelope = AdminNotificationService.envelope("ORDER_NEW", java.util.Map.of("orderId", 4001L));

        org.assertj.core.api.Assertions.assertThat(envelope)
                .contains("\"type\":\"ORDER_NEW\"")
                .contains("\"messageId\":")
                .contains("\"timestamp\":")
                .contains("\"orderId\":4001");
    }
}
