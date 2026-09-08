package com.sky.token;

import com.sky.constant.JwtClaimsConstant;
import com.sky.properties.JwtProperties;
import com.sky.utils.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * JWT 令牌签发服务：按端侧分离令牌配置与声明字段，统一收敛令牌生成逻辑。
 *
 * <p>管理端与用户端使用各自的密钥与有效期，登录鉴权通过后由具体的 service 调用
 * {@link #createAdminToken(Long)} / {@link #createUserToken(Long)} 生成令牌。</p>
 */
@Service
@RequiredArgsConstructor
public class JwtTokenService {

    private final JwtProperties jwtProperties;

    /**
     * 为管理端员工签发令牌。
     *
     * @param empId 员工主键
     * @return 携带员工标识(empId)的 JWT 令牌
     */
    public String createAdminToken(Long empId) {
        return createToken(jwtProperties.getAdminSecretKey(),
                jwtProperties.getAdminTtl(),
                JwtClaimsConstant.EMP_ID,
                empId);
    }

    /**
     * 为用户端 C 端用户签发令牌。
     *
     * @param userId 用户主键
     * @return 携带用户标识(userId)的 JWT 令牌
     */
    public String createUserToken(Long userId) {
        return createToken(jwtProperties.getUserSecretKey(),
                jwtProperties.getUserTtl(),
                JwtClaimsConstant.USER_ID,
                userId);
    }

    /**
     * 统一按声明字段构建 claims 并调用工具类签发令牌。
     *
     * @param secretKey 端侧密钥
     * @param ttlMillis 令牌有效时长(毫秒)
     * @param claimKey  写入 claims 的字段名
     * @param id        写入 claims 的主键值
     * @return JWT 令牌
     */
    private String createToken(String secretKey, long ttlMillis, String claimKey, Long id) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(claimKey, id);
        return JwtUtil.createJWT(secretKey, ttlMillis, claims);
    }
}