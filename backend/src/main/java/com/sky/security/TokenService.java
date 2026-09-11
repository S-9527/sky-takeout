package com.sky.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Set;
import java.util.UUID;

import com.sky.common.error.BusinessException;
import com.sky.common.error.ErrorCode;

/**
 * 令牌签发与校验。
 *
 * <p>access token 无状态:只验签名与有效期,不查 Redis。
 * refresh token 有状态:{@code jti} 存在 Redis 里,登出即删,续期时轮换(旧凭证立刻失效)。
 *
 * <p>为了让"禁用员工 / 改密码后旧会话立刻失效"成为可能,额外维护
 * {@code sky:refresh-subject:<audience>:<subjectId>} 集合,支持按主体一次性撤销全部续期凭证。
 * 没有这层索引,就只能靠遍历所有 key,或者干脆放弃撤销能力——后者意味着被禁用的员工还能继续操作两小时。
 */
@Service
public class TokenService {

    private static final String REFRESH_KEY_PREFIX = "sky:refresh:";
    private static final String SUBJECT_KEY_PREFIX = "sky:refresh-subject:";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_TYPE = "typ";
    private static final int MIN_SECRET_BYTES = 32;

    private final JwtProperties properties;
    private final SecretKey key;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public TokenService(JwtProperties properties, StringRedisTemplate redis, ObjectMapper objectMapper) {
        byte[] secretBytes = properties.secret() == null
                ? new byte[0]
                : properties.secret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "sky.jwt.secret 至少需要 " + MIN_SECRET_BYTES + " 字节,当前只有 " + secretBytes.length + " 字节");
        }
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(secretBytes);
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    /** 一次登录签发的令牌对。TTL 单位秒,便于直接透传给前端。 */
    public record IssuedTokens(String accessToken, long accessExpiresIn, String refreshToken, long refreshExpiresIn) {
    }

    /** access token 解析结果。 */
    public record TokenPayload(Long subjectId, Audience audience, String role) {
    }

    /** refresh token 在 Redis 中的会话内容。 */
    private record RefreshSession(Long subjectId, String role) {
    }

    /** 为指定主体签发令牌对,并把 refresh 会话写入 Redis。 */
    public IssuedTokens issue(Long subjectId, Audience audience, String role) {
        Duration accessTtl = properties.accessTtl(audience);
        Duration refreshTtl = properties.refreshTtl(audience);

        String accessToken = buildToken(subjectId, audience, role, TYPE_ACCESS,
                UUID.randomUUID().toString().replace("-", ""), accessTtl);
        String refreshJti = UUID.randomUUID().toString().replace("-", "");
        String refreshToken = buildToken(subjectId, audience, role, TYPE_REFRESH, refreshJti, refreshTtl);

        redis.opsForValue().set(
                refreshKey(audience, refreshJti),
                serialize(new RefreshSession(subjectId, role)),
                refreshTtl);

        String subjectKey = subjectKey(audience, subjectId);
        redis.opsForSet().add(subjectKey, refreshJti);
        redis.expire(subjectKey, refreshTtl);

        return new IssuedTokens(accessToken, accessTtl.toSeconds(), refreshToken, refreshTtl.toSeconds());
    }

    /** 校验 access token,返回主体信息。 */
    public TokenPayload verifyAccess(String token) {
        Claims claims = parse(token, AuthErrorCode.AUTH_TOKEN_EXPIRED, AuthErrorCode.AUTH_TOKEN_INVALID);
        if (!TYPE_ACCESS.equals(claims.get(CLAIM_TYPE, String.class))) {
            throw new BusinessException(AuthErrorCode.AUTH_TOKEN_INVALID, "令牌类型不正确");
        }
        return toPayload(claims);
    }

    /** 用 refresh token 换一对新令牌,旧 refresh token 立即失效。 */
    public IssuedTokens rotate(String refreshToken) {
        Claims claims = parse(refreshToken,
                AuthErrorCode.AUTH_REFRESH_TOKEN_EXPIRED, AuthErrorCode.AUTH_REFRESH_TOKEN_INVALID);
        if (!TYPE_REFRESH.equals(claims.get(CLAIM_TYPE, String.class))) {
            throw new BusinessException(AuthErrorCode.AUTH_REFRESH_TOKEN_INVALID, "令牌类型不正确");
        }

        Audience audience = audienceOf(claims);
        Long subjectId = Long.valueOf(claims.getSubject());
        String redisKey = refreshKey(audience, claims.getId());
        String stored = redis.opsForValue().get(redisKey);
        if (stored == null) {
            throw new BusinessException(AuthErrorCode.AUTH_REFRESH_TOKEN_INVALID, "续期凭证已失效,请重新登录");
        }
        dropRefresh(audience, subjectId, claims.getId());

        RefreshSession session = deserialize(stored);
        return issue(session.subjectId(), audience, session.role());
    }

    /** 登出:删除该 refresh 会话。access token 依靠自身短有效期自然过期。 */
    public void revoke(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        try {
            Claims claims = parse(refreshToken,
                    AuthErrorCode.AUTH_REFRESH_TOKEN_EXPIRED, AuthErrorCode.AUTH_REFRESH_TOKEN_INVALID);
            Audience audience = audienceOf(claims);
            dropRefresh(audience, Long.valueOf(claims.getSubject()), claims.getId());
        } catch (BusinessException ignored) {
            // 凭证本身已无效,登出视为成功——登出接口不该因为凭证过期而报错
        }
    }

    /**
     * 撤销某主体的全部续期凭证。用于禁用员工、修改密码等需要"立刻踢下线"的场景。
     *
     * <p>注意:已签发的 access token 仍然有效,直到它自然过期(最长 2 小时)。
     * 这是无状态令牌的固有代价;要彻底即时失效必须每次请求查一次状态,不值得。
     */
    public void revokeAll(Audience audience, Long subjectId) {
        String subjectKey = subjectKey(audience, subjectId);
        Set<String> jtis = redis.opsForSet().members(subjectKey);
        if (jtis != null) {
            jtis.forEach(jti -> redis.delete(refreshKey(audience, jti)));
        }
        redis.delete(subjectKey);
    }

    private void dropRefresh(Audience audience, Long subjectId, String jti) {
        redis.delete(refreshKey(audience, jti));
        redis.opsForSet().remove(subjectKey(audience, subjectId), jti);
    }

    private String buildToken(Long subjectId, Audience audience, String role, String type, String jti, Duration ttl) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(properties.issuer())
                .subject(String.valueOf(subjectId))
                .audience().add(audience.name()).and()
                .id(jti)
                .claim(CLAIM_ROLE, role)
                .claim(CLAIM_TYPE, type)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    private Claims parse(String token, ErrorCode expiredCode, ErrorCode invalidCode) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(properties.issuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException ex) {
            throw new BusinessException(expiredCode);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new BusinessException(invalidCode);
        }
    }

    private TokenPayload toPayload(Claims claims) {
        return new TokenPayload(
                Long.valueOf(claims.getSubject()),
                audienceOf(claims),
                claims.get(CLAIM_ROLE, String.class));
    }

    private Audience audienceOf(Claims claims) {
        Set<String> audiences = claims.getAudience();
        if (audiences == null || audiences.isEmpty()) {
            throw new BusinessException(AuthErrorCode.AUTH_TOKEN_INVALID, "令牌缺少受众");
        }
        try {
            return Audience.valueOf(audiences.iterator().next());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(AuthErrorCode.AUTH_TOKEN_INVALID, "令牌受众不合法");
        }
    }

    private static String refreshKey(Audience audience, String jti) {
        return REFRESH_KEY_PREFIX + audience.name().toLowerCase() + ":" + jti;
    }

    private static String subjectKey(Audience audience, Long subjectId) {
        return SUBJECT_KEY_PREFIX + audience.name().toLowerCase() + ":" + subjectId;
    }

    private String serialize(RefreshSession session) {
        try {
            return objectMapper.writeValueAsString(session);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("refresh 会话序列化失败", ex);
        }
    }

    private RefreshSession deserialize(String json) {
        try {
            return objectMapper.readValue(json, RefreshSession.class);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(AuthErrorCode.AUTH_REFRESH_TOKEN_INVALID);
        }
    }
}
