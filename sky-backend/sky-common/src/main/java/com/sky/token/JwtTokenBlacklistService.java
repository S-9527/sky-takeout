package com.sky.token;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * JWT 令牌撤销服务：基于 Redis 保存已撤销令牌的 jti，实现服务端登出/令牌失效。
 */
@Service
@RequiredArgsConstructor
public class JwtTokenBlacklistService {

    private static final String KEY_PREFIX = "sky:jwt:blacklist:";

    private final StringRedisTemplate redisTemplate;

    /**
     * 将指定 jti 标记为已撤销，有效期为令牌剩余有效期。
     *
     * @param jti       令牌唯一标识
     * @param ttlMillis 剩余有效时长(毫秒)
     */
    public void revoke(String jti, long ttlMillis) {
        if (jti == null || jti.isEmpty() || ttlMillis <= 0) {
            return;
        }
        redisTemplate.opsForValue().set(KEY_PREFIX + jti, "1", ttlMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * 判断令牌是否已被撤销。
     *
     * @param jti 令牌唯一标识
     * @return true 表示已撤销
     */
    public boolean isRevoked(String jti) {
        return jti != null && Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + jti));
    }
}