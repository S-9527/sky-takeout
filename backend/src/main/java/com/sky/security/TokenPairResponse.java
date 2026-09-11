package com.sky.security;

/**
 * 对外返回的令牌对。员工端与顾客端共用同一形状。
 *
 * <p>登录接口**只**返回令牌,不返回用户资料:前端登录后再调一次 {@code /auth/me} 或
 * {@code /profile} 拿资料。这样"登录"这件事在任何一端都只有一种形状,
 * 新增/删除资料字段不会牵动登录响应。
 */
public record TokenPairResponse(
        String accessToken,
        String refreshToken,
        long expiresIn,
        long refreshExpiresIn,
        String tokenType
) {

    private static final String BEARER = "Bearer";

    public static TokenPairResponse from(TokenService.IssuedTokens tokens) {
        return new TokenPairResponse(
                tokens.accessToken(),
                tokens.refreshToken(),
                tokens.accessExpiresIn(),
                tokens.refreshExpiresIn(),
                BEARER);
    }
}
