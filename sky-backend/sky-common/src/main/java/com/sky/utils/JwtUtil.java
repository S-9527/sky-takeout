package com.sky.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

public class JwtUtil {
    /**
     * 校验时允许的时钟偏移阈值(秒)，用于容忍客户端/服务端时间偏差
     */
    public static final long CLOCK_SKEW_SECONDS = 60L;

    private static SecretKey hmacKey(String secretKey) {
        return Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 签发 JWT（HS256）。补齐标准注册声明：
     * jti(唯一标识)、iat(签发时间)、nbf(生效时间)、exp(过期时间)、iss(签发方)、aud(受众)，
     * 并在 header 写入 kid(密钥标识)以支持多密钥轮换。
     *
     * @param secretKey 哈希密钥(长度需 >=32 字节)
     * @param ttlMillis 令牌有效时长(毫秒)
     * @param issuer    签发方
     * @param audience  受众
     * @param keyId     密钥标识(header中)
     * @param claims    业务声明(如 empId/userId)
     * @return JWT 令牌
     */
    public static String createJWT(String secretKey, long ttlMillis, String issuer, String audience,
                                   String keyId, Map<String, Object> claims) {
        Date now = new Date();
        Date exp = new Date(System.currentTimeMillis() + ttlMillis);

        JwtBuilder builder = Jwts.builder()
                .header().keyId(keyId).and()
                .id(UUID.randomUUID().toString())
                .issuer(issuer)
                .audience().single(audience)
                .issuedAt(now)
                .notBefore(now)
                .expiration(exp)
                .claims(claims)
                .signWith(hmacKey(secretKey), Jwts.SIG.HS256);
        return builder.compact();
    }

    /**
     * 校验并解析 JWT：验证签名(按 kid 选择密钥)、校验 jti/iat/nbf/exp，
     * 并允许 60s 时钟偏移。
     *
     * @param keysByKeyId kid → 密钥映射（轮换时填入当前及历史密钥）
     * @param token       JWT 令牌
     * @return 解析后的声明
     */
    public static Claims parseJWT(Map<String, SecretKey> keysByKeyId, String token) {
        return Jwts.parser()
                .clockSkewSeconds(CLOCK_SKEW_SECONDS)
                .keyLocator((io.jsonwebtoken.Locator<Key>) header -> {
                    String keyId = (String) header.get("kid");
                    return keysByKeyId.get(keyId);
                })
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 使用单个密钥校验并解析 JWT（兼容旧调用，便于测试）。
     *
     * @param secretKey 哈希密钥
     * @param token     JWT 令牌
     * @return 解析后的声明
     */
    public static Claims parseJWT(String secretKey, String token) {
        return Jwts.parser()
                .clockSkewSeconds(CLOCK_SKEW_SECONDS)
                .verifyWith(hmacKey(secretKey))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

}