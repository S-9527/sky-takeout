package com.sky.auth.token;

/**
 * 令牌端侧类型：聚合该端侧 JWT 声明的字段名、签发方(issuer)与 Spring Security 角色，
 * 使签发与验签使用同一组契约，避免 claimKey/issuer/role 三处魔法字符串失配。
 */
public enum TokenType {

    ADMIN("empId", "sky-admin", "ROLE_ADMIN"),
    USER("userId", "sky-user", "ROLE_USER");

    /**
     * token 载荷中标识主键的声明字段名
     */
    private final String claimKey;

    /**
     * 签发该端侧令牌时使用的 issuer
     */
    private final String issuer;

    /**
     * 该端侧映射的 Spring Security 角色(带 ROLE_ 前缀)
     */
    private final String springRole;

    TokenType(String claimKey, String issuer, String springRole) {
        this.claimKey = claimKey;
        this.issuer = issuer;
        this.springRole = springRole;
    }

    public String getClaimKey() {
        return claimKey;
    }

    public String getIssuer() {
        return issuer;
    }

    public String getSpringRole() {
        return springRole;
    }
}