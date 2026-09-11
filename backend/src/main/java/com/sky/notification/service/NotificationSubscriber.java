package com.sky.notification.service;

import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 订阅管理端通知频道,把消息转给本实例持有的 WebSocket 会话。
 *
 * <p>集群下每个实例都会收到同一条消息,各自推给自己的连接——这正是"所有连接都能收到"的实现方式。
 */
@Component
public class NotificationSubscriber implements MessageListener {

    private final AdminNotificationWebSocketHandler handler;

    public NotificationSubscriber(AdminNotificationWebSocketHandler handler) {
        this.handler = handler;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        handler.broadcastLocal(new String(message.getBody(), StandardCharsets.UTF_8));
    }
}
