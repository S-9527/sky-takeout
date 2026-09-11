package com.sky.payment.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.sky.common.util.Times;

/** 退款单号:{@code RF} + yyyyMMddHHmm + 每分钟 6 位序号(与订单号同一套思路)。 */
@Component
public class RefundNoGenerator {

    private static final DateTimeFormatter PREFIX_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
    private static final String KEY_PREFIX = "sky:refund:no:";
    private static final Duration KEY_TTL = Duration.ofDays(2);

    private final StringRedisTemplate redis;

    public RefundNoGenerator(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public String next() {
        String prefix = "RF" + LocalDateTime.now(Times.ZONE).format(PREFIX_FORMAT);
        String key = KEY_PREFIX + prefix;
        Long sequence = redis.opsForValue().increment(key);
        if (sequence == null) {
            throw new IllegalStateException("生成退款单号失败:Redis INCR 未返回结果");
        }
        redis.expire(key, KEY_TTL);
        return prefix + String.format("%06d", sequence);
    }
}
