package com.sky.order.event.listener;

import com.alibaba.fastjson.JSON;
import com.sky.order.enumeration.OrderStatus;
import com.sky.order.event.OrderReminderEvent;
import com.sky.order.event.OrderStatusChangedEvent;
import com.sky.websocket.WebSocketServer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.HashMap;
import java.util.Map;

/**
 * 订单领域事件监听器：将业务事件转换为对管理端/客户端的 WebSocket 推送，
 * 使得订单服务不再反向依赖 WebSocket 基建。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class OrderEventListener {

    private final WebSocketServer webSocketServer;

    /**
     * 支付成功转入待接单时，推送来单提醒
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderStatusChanged(OrderStatusChangedEvent event) {
        if (event.getTo() == OrderStatus.TO_BE_CONFIRMED) {
            push(1, event.getOrderId(), "订单号：" + event.getOrderNumber());
        }
    }

    /**
     * 客户催单
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderReminder(OrderReminderEvent event) {
        push(2, event.getOrderId(), "订单号：" + event.getOrderNumber());
    }

    private void push(int type, Long orderId, String content) {
        Map<String, Object> map = new HashMap<>();
        map.put("type", type); // 1来单提醒 2客户催单
        map.put("orderId", orderId);
        map.put("content", content);
        webSocketServer.sendToAllClient(JSON.toJSONString(map));
    }
}