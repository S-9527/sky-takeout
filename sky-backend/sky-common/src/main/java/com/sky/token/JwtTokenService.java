package com.sky.token;

import com.sky.constant.MessageConstant;
import com.sky.exception.BaseException;
import com.sky.properties.JwtKeyConfig;
import com.sky.properties.JwtProperties;
import com.sky.result.ResultCode;
import com.sky.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JWT 令牌签发服务：按端侧类型({@link TokenType})与令牌用途({@link TokenPurpose})
 * 分离配置与声明字段，统一收敛双令牌(访问/刷新)的签发、验签、轮换、撤销与黑名单校验。
 *
 * <p>签发补齐标准注册声明(jti/iat/nbf/exp/iss/aud)并在 header 写 kid 以支持多密钥轮换；
 * 登录鉴权通过后由具体的 service 调用
 * {@link #createTokenPair(TokenType, Long)} 生成访问+刷新令牌对。</p>
 */
@Service
@RequiredArgsConstructor
public class JwtTokenService {

    private final JwtProperties jwtProperties;
    private final JwtTokenBlacklistService blacklistService;

    /**
     * 为管理端员工签发访问令牌。
     *
     * @param empId 员工主键
     * @return 携带员工标识的短效访问令牌
     */
    public String createAdminAccessToken(Long empId) {
        return createToken(TokenType.ADMIN, TokenPurpose.ACCESS, empId,
                jwtProperties.getAdminKeys(), jwtProperties.getAdminTtl());
    }

    /**
     * 为管理端员工签发刷新令牌。
     *
     * @param empId 员工主键
     * @return 携带员工标识的长效刷新令牌
     */
    public String createAdminRefreshToken(Long empId) {
        return createToken(TokenType.ADMIN, TokenPurpose.REFRESH, empId,
                jwtProperties.getAdminKeys(), jwtProperties.getAdminRefreshTtl());
    }

    /**
     * 为用户端 C 端用户签发访问令牌。
     *
     * @param userId 用户主键
     * @return 携带用户标识的短效访问令牌
     */
    public String createUserAccessToken(Long userId) {
        return createToken(TokenType.USER, TokenPurpose.ACCESS, userId,
                jwtProperties.getUserKeys(), jwtProperties.getUserTtl());
    }

    /**
     * 为用户端 C 端用户签发刷新令牌。
     *
     * @param userId 用户主键
     * @return 携带用户标识的长效刷新令牌
     */
    public String createUserRefreshToken(Long userId) {
        return createToken(TokenType.USER, TokenPurpose.REFRESH, userId,
                jwtProperties.getUserKeys(), jwtProperties.getUserRefreshTtl());
    }

    /**
     * 签发访问+刷新令牌对，登录成功后统一返回。
     *
     * @param type 端侧类型
     * @param id   主键(员工/用户)
     * @return 令牌对
     */
    public TokenPair createTokenPair(TokenType type, Long id) {
        return type == TokenType.ADMIN
                ? new TokenPair(createAdminAccessToken(id), createAdminRefreshToken(id))
                : new TokenPair(createUserAccessToken(id), createUserRefreshToken(id));
    }

    /**
     * 校验访问令牌(含签名、iss/aud、jti、exp/nbf 与用途)，失败抛出异常。
     *
     * @param token JWT 令牌
     * @param type  端侧类型
     * @return 标准与业务声明
     */
    public Claims parseAccessToken(String token, TokenType type) {
        return parseToken(token, type, TokenPurpose.ACCESS);
    }

    /**
     * 校验刷新令牌(含签名、iss/aud、jti、exp/nbf 与用途)，失败抛出异常。
     *
     * @param token JWT 令牌
     * @param type  端侧类型
     * @return 标准与业务声明
     */
    public Claims parseRefreshToken(String token, TokenType type) {
        return parseToken(token, type, TokenPurpose.REFRESH);
    }

    /**
     * 免用途校验地解析令牌：用于撤销场景(黑名单按 jti，不关心用途)。
     *
     * @param token JWT 令牌
     * @param type  端侧类型
     * @return 标准与业务声明
     */
    public Claims parseToken(String token, TokenType type) {
        return JwtUtil.parseJWT(keysToMap(keysOf(type)), type.getIssuer(),
                jwtProperties.getAudience(), token);
    }

    /**
     * 刷新令牌对：校验旧刷新令牌有效且未被撤销后，撤销旧令牌并签发新的访问+刷新令牌对(轮换)。
     *
     * @param type         端侧类型
     * @param refreshToken 待轮换的刷新令牌
     * @return 新的令牌对
     */
    public TokenPair refresh(TokenType type, String refreshToken) {
        Claims claims = parseToken(refreshToken, type, TokenPurpose.REFRESH);
        if (blacklistService.isRevoked(claims.getId())) {
            throw new BaseException(ResultCode.UNAUTHORIZED.getCode(),
                    MessageConstant.REFRESH_TOKEN_INVALID);
        }
        // 轮换：旧刷新令牌立即进黑名单，防止重放
        revokeToken(refreshToken, type);
        Long id = Long.valueOf(claims.get(type.getClaimKey()).toString());
        return createTokenPair(type, id);
    }

    /**
     * 撤销指定令牌：解析出 jti 与剩余有效期后写入黑名单，令牌即刻失效。
     *
     * @param token 待撤销的 JWT 令牌(访问或刷新)
     * @param type  端侧类型(决定验签密钥与 issuer 校验)
     */
    public void revokeToken(String token, TokenType type) {
        Claims claims = parseToken(token, type);
        long remaining = claims.getExpiration().getTime() - System.currentTimeMillis();
        blacklistService.revoke(claims.getId(), remaining);
    }

    /**
     * 判断令牌是否已被撤销。
     *
     * @param token JWT 令牌
     * @param type  端侧类型
     * @return true 表示已撤销
     */
    public boolean isRevoked(String token, TokenType type) {
        Claims claims = parseToken(token, type);
        return blacklistService.isRevoked(claims.getId());
    }

    private String createToken(TokenType type, TokenPurpose purpose, Long id,
                               List<JwtKeyConfig> keys, long ttlMillis) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(type.getClaimKey(), id);
        claims.put(TokenPurpose.CLAIM_KEY, purpose.getValue());
        return JwtUtil.createJWT(keys.get(0).getSecret(), ttlMillis, type.getIssuer(),
                jwtProperties.getAudience(), keys.get(0).getKid(), claims);
    }

    private Claims parseToken(String token, TokenType type, TokenPurpose purpose) {
        Claims claims = JwtUtil.parseJWT(keysToMap(keysOf(type)), type.getIssuer(),
                jwtProperties.getAudience(), token);
        if (!purpose.matches(claims.get(TokenPurpose.CLAIM_KEY))) {
            throw new BaseException(ResultCode.UNAUTHORIZED.getCode(),
                    MessageConstant.TOKEN_TYPE_MISMATCH);
        }
        return claims;
    }

    private List<JwtKeyConfig> keysOf(TokenType type) {
        return type == TokenType.ADMIN ? jwtProperties.getAdminKeys() : jwtProperties.getUserKeys();
    }

    private Map<String, SecretKey> keysToMap(List<JwtKeyConfig> keys) {
        Map<String, SecretKey> map = new HashMap<>();
        for (JwtKeyConfig key : keys) {
            map.put(key.getKid(), new SecretKeySpec(
                    key.getSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        }
        return map;
    }
}