package com.sky.auth.token;

/**
 * 令牌用途：区分访问令牌(access)与刷新令牌(refresh)，
 * 在 JWT 载荷中以 {@link #CLAIM_KEY} 声明区分，避免刷新令牌被当作访问令牌使用。
 */
public enum TokenPurpose {

    ACCESS("access"),
    REFRESH("refresh");

    /**
     * JWT 载荷中标识令牌用途的声明字段名
     */
    public static final String CLAIM_KEY = "token_type";

    private final String value;

    TokenPurpose(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /**
     * 判断声明值是否与当前用途匹配。
     *
     * @param claimValue 载荷中 token_type 的原始值
     * @return true 表示匹配
     */
    public boolean matches(Object claimValue) {
        return value.equals(claimValue);
    }
}