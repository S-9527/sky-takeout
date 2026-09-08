package com.sky.token;

import com.sky.constant.JwtClaimsConstant;
import com.sky.properties.JwtKeyConfig;
import com.sky.properties.JwtProperties;
import com.sky.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT 令牌签发服务：按端侧分离令牌配置与声明字段，统一收敛令牌生成、验签与撤销逻辑。
 *
 * <p>签发补齐标准注册声明(jti/iat/nbf/exp/iss/aud)并在 header 写 kid 以支持多密钥轮换；
 * 登录鉴权通过后由具体的 service 调用
 * {@link #createAdminToken(Long)} / {@link #createUserToken(Long)} 生成令牌。</p>
 */
@Service
@RequiredArgsConstructor
public class JwtTokenService {

    private final JwtProperties jwtProperties;
    private final JwtTokenBlacklistService blacklistService;

    /**
     * 为管理端员工签发令牌。
     *
     * @param empId 员工主键
     * @return 携带员工标识(empId)与标准声明的 JWT 令牌
     */
    public String createAdminToken(Long empId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.EMP_ID, empId);
        claims.put(JwtClaimsConstant.ROLE, "ADMIN");
        return createToken(currentKey(jwtProperties.getAdminKeys()),
                jwtProperties.getAdminIssuer(),
                jwtProperties.getAdminTtl(),
                claims);
    }

    /**
     * 为用户端 C 端用户签发令牌。
     *
     * @param userId 用户主键
     * @return 携带用户标识(userId)与标准声明的 JWT 令牌
     */
    public String createUserToken(Long userId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.USER_ID, userId);
        claims.put(JwtClaimsConstant.ROLE, "USER");
        return createToken(currentKey(jwtProperties.getUserKeys()),
                jwtProperties.getUserIssuer(),
                jwtProperties.getUserTtl(),
                claims);
    }

    /**
     * 解析并校验令牌(含签名、iss/aud、jti、exp/nbf)，返回声明；失败抛出异常。
     *
     * @param token JWT 令牌
     * @return 标准与业务声明
     */
    public Claims parseToken(String token) {
        return JwtUtil.parseJWT(allAdminKeys(), token);
    }

    /**
     * 解析用户侧令牌。
     *
     * @param token JWT 令牌
     * @return 标准与业务声明
     */
    public Claims parseUserToken(String token) {
        return JwtUtil.parseJWT(allUserKeys(), token);
    }

    /**
     * 撤销指定令牌：解析出 jti 与剩余有效期后写入黑名单，令牌即刻失效。
     *
     * @param token   待撤销的 JWT 令牌
     * @param isAdmin 是否管理端令牌(决定验签密钥)
     */
    public void revokeToken(String token, boolean isAdmin) {
        Claims claims = isAdmin ? parseToken(token) : parseUserToken(token);
        long remaining = claims.getExpiration().getTime() - System.currentTimeMillis();
        blacklistService.revoke(claims.getId(), remaining);
    }

    /**
     * 判断令牌是否已被撤销。
     *
     * @param token JWT 令牌
     * @return true 表示已撤销
     */
    public boolean isRevoked(String token, boolean isAdmin) {
        Claims claims = isAdmin ? parseToken(token) : parseUserToken(token);
        return blacklistService.isRevoked(claims.getId());
    }

    private String createToken(JwtKeyConfig key, String issuer, long ttlMillis, Map<String, Object> claims) {
        return JwtUtil.createJWT(key.getSecret(), ttlMillis, issuer,
                jwtProperties.getAudience(), key.getKid(), claims);
    }

    private JwtKeyConfig currentKey(java.util.List<JwtKeyConfig> keys) {
        return keys.get(0);
    }

    private Map<String, SecretKey> allAdminKeys() {
        return keysToMap(jwtProperties.getAdminKeys());
    }

    private Map<String, SecretKey> allUserKeys() {
        return keysToMap(jwtProperties.getUserKeys());
    }

    private Map<String, SecretKey> keysToMap(java.util.List<JwtKeyConfig> keys) {
        Map<String, SecretKey> map = new HashMap<>();
        for (JwtKeyConfig key : keys) {
            map.put(key.getKid(), new SecretKeySpec(
                    key.getSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        }
        return map;
    }
}