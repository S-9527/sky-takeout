package com.sky.order.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.sky.common.util.Times;

/**
 * 业务单号生成器:形如 {@code 202501011200000001}(yyyyMMddHHmm + 每分钟 6 位序号)。
 *
 * <p>用 Redis 的 {@code INCR} 做每分钟序列:单号要唯一(库里有唯一键),又希望它"看起来是时间序",
 * 便于人工排查与客服检索。序号超过 6 位就自然变长(列宽 32),不做取模——取模会造成重复单号。
 */
@Component
public class OrderNoGenerator {

    private static final DateTimeFormatter PREFIX_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
    private static final String KEY_PREFIX = "sky:order:no:";

    /** 序号键只服务于单号,留两天足够覆盖时区/补单场景。 */
    private static final Duration KEY_TTL = Duration.ofDays(2);

    private final StringRedisTemplate redis;

    public OrderNoGenerator(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public String next() {
        String prefix = LocalDateTime.now(Times.ZONE).format(PREFIX_FORMAT);
        String key = KEY_PREFIX + prefix;
        Long sequence = redis.opsForValue().increment(key);
        if (sequence == null) {
            throw new IllegalStateException("生成订单号失败:Redis INCR 未返回结果");
        }
        redis.expire(key, KEY_TTL);
        return prefix + String.format("%06d", sequence);
    }
}
