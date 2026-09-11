package com.sky.notification.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.sky.common.util.Json;
import com.sky.common.util.Times;

/**
 * 管理端通知的发送端:把消息包成契约 §4.2 的信封,发到 Redis Pub/Sub。
 *
 * <p>为什么走 Redis 而不是直接给本机会话发:契约 §4.1 要求多实例下**所有**连接都能收到。
 * 每个实例订阅同一个频道,收到后只推给自己持有的会话。
 */
@Service
public class AdminNotificationService {

    /** 管理端通知频道。 */
    public static final String CHANNEL = "sky:notify:admin";

    private final StringRedisTemplate redis;

    public AdminNotificationService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** 广播一条通知(所有实例的所有管理端连接)。 */
    public void send(String type, Object payload) {
        redis.convertAndSend(CHANNEL, envelope(type, payload));
    }

    /** 组装契约 §4.2 的信封:{type, messageId, timestamp, payload}。 */
    public static String envelope(String type, Object payload) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("type", type);
        envelope.put("messageId", UUID.randomUUID().toString());
        envelope.put("timestamp", java.time.OffsetDateTime.now(Times.ZONE)
                .format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        envelope.put("payload", payload == null ? Map.of() : payload);
        return Json.write(envelope);
    }
}
