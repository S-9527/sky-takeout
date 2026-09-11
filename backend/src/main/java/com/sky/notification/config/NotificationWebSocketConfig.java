package com.sky.notification.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.sky.config.WebProperties;
import com.sky.notification.service.AdminNotificationService;
import com.sky.notification.service.AdminNotificationWebSocketHandler;
import com.sky.notification.service.NotificationSubscriber;

/** 管理端通知的 WebSocket 端点与 Redis Pub/Sub 订阅装配。 */
@Configuration
@EnableWebSocket
public class NotificationWebSocketConfig implements WebSocketConfigurer {

    /** 契约 §4.1 的路径。 */
    public static final String ENDPOINT = "/ws/admin/notifications";

    private final AdminNotificationWebSocketHandler handler;
    private final WebProperties webProperties;

    public NotificationWebSocketConfig(AdminNotificationWebSocketHandler handler, WebProperties webProperties) {
        this.handler = handler;
        this.webProperties = webProperties;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 浏览器 WebSocket 不能自定义请求头,令牌走查询参数,所以在 SecurityConfig 里放行 /ws/**,
        // 真正的鉴权在 handler 建立连接之后按契约用关闭码表达
        registry.addHandler(handler, ENDPOINT)
                .setAllowedOriginPatterns(webProperties.corsAllowedOriginPatterns().toArray(String[]::new));
    }

    @Bean
    public RedisMessageListenerContainer notificationListenerContainer(RedisConnectionFactory connectionFactory,
                                                                       NotificationSubscriber subscriber) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(subscriber, new ChannelTopic(AdminNotificationService.CHANNEL));
        return container;
    }
}
