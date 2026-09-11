package com.sky.security;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
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
 * <p><b>续期凭证</b>有状态:{@code jti} 存在 Redis 里,登出即删,续期时轮换(旧凭证立刻失效)。
 *
 * <p><b>访问令牌</b>本身无状态(不查库),但带一个"会话世代"声明 {@code ep}。
 * 每个主体在 Redis 里有一个世代号,校验时比对:令牌世代低于当前世代即视为已撤销。
 * 这样"禁用员工 / 修改密码"能立刻让该员工手上所有 access token 失效,
 * 而代价只是每次请求一次 Redis GET——不是一次数据库查询,也不是放弃撤销能力。
 *
 * <p>世代号而不是"撤销时间戳":JWT 的 {@code iat} 只有秒级精度,
 * 用时间戳会出现"同一秒内先撤销再重新登录,新令牌被误杀"的问题。世代号是单调计数器,没有这个边界。
 */
@Service
public class TokenService {

    private static final String REFRESH_KEY_PREFIX = "sky:refresh:";
    private static final String SUBJECT_KEY_PREFIX = "sky:refresh-subject:";
    private static final String EPOCH_KEY_PREFIX = "sky:auth:epoch:";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_TYPE = "typ";
    private static final String CLAIM_EPOCH = "ep";
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
        long epoch = currentEpoch(audience, subjectId);

        String accessToken = buildToken(subjectId, audience, role, TYPE_ACCESS,
                UUID.randomUUID().toString().replace("-", ""), accessTtl, epoch);
        String refreshJti = UUID.randomUUID().toString().replace("-", "");
        String refreshToken = buildToken(subjectId, audience, role, TYPE_REFRESH, refreshJti, refreshTtl, epoch);

        redis.opsForValue().set(
                refreshKey(audience, refreshJti),
                serialize(new RefreshSession(subjectId, role)),
                refreshTtl);

        String subjectKey = subjectKey(audience, subjectId);
        redis.opsForSet().add(subjectKey, refreshJti);
        redis.expire(subjectKey, refreshTtl);

        return new IssuedTokens(accessToken, accessTtl.toSeconds(), refreshToken, refreshTtl.toSeconds());
    }

    /** 校验 access token,返回主体信息;已被撤销的令牌抛 401。 */
    public TokenPayload verifyAccess(String token) {
        Claims claims = parse(token, AuthErrorCode.AUTH_TOKEN_EXPIRED, AuthErrorCode.AUTH_TOKEN_INVALID);
        if (!TYPE_ACCESS.equals(claims.get(CLAIM_TYPE, String.class))) {
            throw new BusinessException(AuthErrorCode.AUTH_TOKEN_INVALID, "令牌类型不正确");
        }
        Audience audience = audienceOf(claims);
        Long subjectId = Long.valueOf(claims.getSubject());

        Long tokenEpoch = claims.get(CLAIM_EPOCH, Long.class);
        if (tokenEpoch == null || tokenEpoch < currentEpoch(audience, subjectId)) {
            // 主体被禁用或改过密码,该世代之前的令牌一律作废
            throw new BusinessException(AuthErrorCode.AUTH_TOKEN_INVALID, "登录状态已失效,请重新登录");
        }
        return new TokenPayload(subjectId, audience, claims.get(CLAIM_ROLE, String.class));
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
     * 撤销某主体的全部会话:删除续期凭证,并把会话世代 +1。
     *
     * <p>世代 +1 之后,该主体此前签发的**所有** access token 在校验时都会被判为失效,
     * 所以"禁用员工 / 修改密码"是立刻生效的,不留 2 小时空窗。
     */
    public void revokeAll(Audience audience, Long subjectId) {
        String subjectKey = subjectKey(audience, subjectId);
        Set<String> jtis = redis.opsForSet().members(subjectKey);
        if (jtis != null) {
            jtis.forEach(jti -> redis.delete(refreshKey(audience, jti)));
        }
        redis.delete(subjectKey);

        String epochKey = epochKey(audience, subjectId);
        redis.opsForValue().increment(epochKey);
        // TTL 只需覆盖最长的 access 有效期即可:世代号过期后归零,
        // 而那时旧令牌早已自然过期,不会因此复活。
        redis.expire(epochKey, properties.refreshTtl(audience));
    }

    private long currentEpoch(Audience audience, Long subjectId) {
        String value = redis.opsForValue().get(epochKey(audience, subjectId));
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private void dropRefresh(Audience audience, Long subjectId, String jti) {
        redis.delete(refreshKey(audience, jti));
        redis.opsForSet().remove(subjectKey(audience, subjectId), jti);
    }

    private String buildToken(Long subjectId, Audience audience, String role, String type, String jti,
                              Duration ttl, long epoch) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(properties.issuer())
                .subject(String.valueOf(subjectId))
                .audience().add(audience.name()).and()
                .id(jti)
                .claim(CLAIM_ROLE, role)
                .claim(CLAIM_TYPE, type)
                .claim(CLAIM_EPOCH, epoch)
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

    private static String epochKey(Audience audience, Long subjectId) {
        return EPOCH_KEY_PREFIX + audience.name().toLowerCase() + ":" + subjectId;
    }

    private String serialize(RefreshSession session) {
        return objectMapper.writeValueAsString(session);
    }

    private RefreshSession deserialize(String json) {
        try {
            return objectMapper.readValue(json, RefreshSession.class);
        } catch (JacksonException ex) {
            throw new BusinessException(AuthErrorCode.AUTH_REFRESH_TOKEN_INVALID);
        }
    }
}
