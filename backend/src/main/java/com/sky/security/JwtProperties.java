package com.sky.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 令牌配置。对应 {@code application.yml} 的 {@code sky.jwt.*}。
 *
 * <p>access token 短命且无状态;refresh token 长命且可撤销(存 Redis),续期时轮换。
 */
@ConfigurationProperties(prefix = "sky.jwt")
public record JwtProperties(
        String issuer,
        String secret,
        Duration adminAccessTtl,
        Duration adminRefreshTtl,
        Duration customerAccessTtl,
        Duration customerRefreshTtl
) {

    public Duration accessTtl(Audience audience) {
        return audience == Audience.ADMIN ? adminAccessTtl : customerAccessTtl;
    }

    public Duration refreshTtl(Audience audience) {
        return audience == Audience.ADMIN ? adminRefreshTtl : customerRefreshTtl;
    }
}
