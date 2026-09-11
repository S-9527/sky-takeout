package com.sky.security;

import tools.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import javax.crypto.SecretKey;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.sky.common.error.BusinessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link TokenService} 的行为测试。
 *
 * <p>Redis 用一个"两张 Map"的假实现(动态代理)而不是嵌入式 Redis:令牌逻辑要验的是
 * 世代号比对、refresh 轮换与撤销,不是 Redis 本身;测试因此不需要任何外部依赖。
 */
class TokenServiceTest {

    private static final String SECRET = "sky-takeout-test-secret-key-at-least-32-bytes";
    private static final String ISSUER = "sky-takeout";
    private static final SecretKey KEY = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    private final Map<String, String> values = new HashMap<>();
    private final Map<String, Set<String>> sets = new HashMap<>();
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private TokenService tokenService;

    @BeforeEach
    void setUp() {
        values.clear();
        sets.clear();

        ValueOperations<String, String> valueOps = fake(ValueOperations.class, (method, args) -> switch (method) {
            case "get" -> values.get((String) args[0]);
            case "set" -> {
                values.put((String) args[0], (String) args[1]);
                yield null;
            }
            case "increment" -> {
                String key = (String) args[0];
                long next = Long.parseLong(values.getOrDefault(key, "0")) + 1;
                values.put(key, String.valueOf(next));
                yield next;
            }
            default -> throw new UnsupportedOperationException(method);
        });

        SetOperations<String, String> setOps = fake(SetOperations.class, (method, args) -> switch (method) {
            // add / remove 都是可变参数:第二个入参是数组,不是一个值
            case "add" -> {
                Set<String> members = sets.computeIfAbsent((String) args[0], key -> new HashSet<>());
                long added = 0;
                for (String value : (String[]) args[1]) {
                    if (members.add(value)) {
                        added++;
                    }
                }
                yield added;
            }
            case "members" -> sets.get((String) args[0]);
            case "remove" -> {
                Set<String> members = sets.get((String) args[0]);
                long removed = 0;
                for (Object value : (Object[]) args[1]) {
                    if (members != null && members.remove(value)) {
                        removed++;
                    }
                }
                yield removed;
            }
            default -> throw new UnsupportedOperationException(method);
        });

        when(redis.opsForValue()).thenReturn(valueOps);
        when(redis.opsForSet()).thenReturn(setOps);
        when(redis.delete(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            boolean removed = values.remove(key) != null;
            removed |= sets.remove(key) != null;
            return removed;
        });
        when(redis.expire(anyString(), any(Duration.class))).thenReturn(true);

        tokenService = new TokenService(properties(Duration.ofHours(2), Duration.ofDays(7)), redis, objectMapper);
    }

    private static JwtProperties properties(Duration accessTtl, Duration refreshTtl) {
        return new JwtProperties(ISSUER, SECRET, accessTtl, refreshTtl, accessTtl, refreshTtl);
    }

    @SuppressWarnings("unchecked")
    private static <T> T fake(Class<T> type, FakeRedisMethod handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> handler.invoke(method.getName(), args == null ? new Object[0] : args));
    }

    private interface FakeRedisMethod {
        Object invoke(String method, Object[] args);
    }

    // ---------------------------------------------------------------- 构造

    @Test
    void rejectsSecretShorterThan32Bytes() {
        assertThatThrownBy(() -> new TokenService(
                new JwtProperties(ISSUER, "too-short", Duration.ofHours(2), Duration.ofDays(7),
                        Duration.ofHours(2), Duration.ofDays(7)),
                redis, objectMapper))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32");
    }

    @Test
    void rejectsNullSecret() {
        assertThatThrownBy(() -> new TokenService(
                new JwtProperties(ISSUER, null, Duration.ofHours(2), Duration.ofDays(7),
                        Duration.ofHours(2), Duration.ofDays(7)),
                redis, objectMapper))
                .isInstanceOf(IllegalStateException.class);
    }

    // ---------------------------------------------------------------- 签发与校验

    @Test
    void issueReturnsBothTokensWithTtlInSeconds() {
        TokenService.IssuedTokens tokens = tokenService.issue(1L, Audience.ADMIN, "ADMIN");

        assertThat(tokens.accessToken()).isNotBlank();
        assertThat(tokens.refreshToken()).isNotBlank();
        assertThat(tokens.accessExpiresIn()).isEqualTo(Duration.ofHours(2).toSeconds());
        assertThat(tokens.refreshExpiresIn()).isEqualTo(Duration.ofDays(7).toSeconds());
        // refresh 会话必须落 Redis(有状态、可撤销),access 不落
        assertThat(values).containsKey(refreshKeyOf(tokens.refreshToken()));
        assertThat(sets.keySet()).anyMatch(key -> key.startsWith("sky:refresh-subject:admin:"));
    }

    @Test
    void verifyAccessReturnsSubjectAudienceAndRole() {
        TokenService.IssuedTokens tokens = tokenService.issue(42L, Audience.ADMIN, "STAFF");

        TokenService.TokenPayload payload = tokenService.verifyAccess(tokens.accessToken());

        assertThat(payload.subjectId()).isEqualTo(42L);
        assertThat(payload.audience()).isEqualTo(Audience.ADMIN);
        assertThat(payload.role()).isEqualTo("STAFF");
    }

    @Test
    void customerTokensCarryCustomerAudience() {
        TokenService.IssuedTokens tokens = tokenService.issue(9L, Audience.CUSTOMER, "CUSTOMER");

        assertThat(tokenService.verifyAccess(tokens.accessToken()).audience()).isEqualTo(Audience.CUSTOMER);
    }

    @Test
    void garbageTokenIsRejected() {
        assertThatThrownBy(() -> tokenService.verifyAccess("not-a-jwt"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(AuthErrorCode.AUTH_TOKEN_INVALID));
    }

    @Test
    void refreshTokenCannotBeUsedAsAccessToken() {
        TokenService.IssuedTokens tokens = tokenService.issue(1L, Audience.ADMIN, "ADMIN");

        assertThatThrownBy(() -> tokenService.verifyAccess(tokens.refreshToken()))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(AuthErrorCode.AUTH_TOKEN_INVALID));
    }

    @Test
    void expiredAccessTokenReportsExpiryNotInvalidity() {
        assertThatThrownBy(() -> tokenService.verifyAccess(
                manualToken("access", "ADMIN", "jti-1", 0L, Instant.now().minusSeconds(5))))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(AuthErrorCode.AUTH_TOKEN_EXPIRED));
    }

    @Test
    void tokenWithoutAudienceIsRejected() {
        assertThatThrownBy(() -> tokenService.verifyAccess(
                manualToken("access", null, "jti-2", 0L, Instant.now().plusSeconds(60))))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(AuthErrorCode.AUTH_TOKEN_INVALID));
    }

    @Test
    void tokenWithUnknownAudienceIsRejected() {
        assertThatThrownBy(() -> tokenService.verifyAccess(
                manualToken("access", "ROBOT", "jti-3", 0L, Instant.now().plusSeconds(60))))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(AuthErrorCode.AUTH_TOKEN_INVALID));
    }

    @Test
    void tokenWithoutEpochClaimIsRejected() {
        assertThatThrownBy(() -> tokenService.verifyAccess(
                manualToken("access", "ADMIN", "jti-4", null, Instant.now().plusSeconds(60))))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(AuthErrorCode.AUTH_TOKEN_INVALID));
    }

    @Test
    void tokenSignedByAnotherIssuerIsRejected() {
        TokenService other = new TokenService(
                new JwtProperties("somebody-else", SECRET, Duration.ofHours(2), Duration.ofDays(7),
                        Duration.ofHours(2), Duration.ofDays(7)),
                redis, objectMapper);
        String foreignToken = other.issue(1L, Audience.ADMIN, "ADMIN").accessToken();

        assertThatThrownBy(() -> tokenService.verifyAccess(foreignToken))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(AuthErrorCode.AUTH_TOKEN_INVALID));
    }

    // ---------------------------------------------------------------- 世代号(立即撤销 access)

    @Test
    void revokeAllInvalidatesAccessTokensIssuedBeforeIt() {
        TokenService.IssuedTokens tokens = tokenService.issue(5L, Audience.ADMIN, "ADMIN");
        assertThat(tokenService.verifyAccess(tokens.accessToken())).isNotNull();

        tokenService.revokeAll(Audience.ADMIN, 5L);

        assertThatThrownBy(() -> tokenService.verifyAccess(tokens.accessToken()))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(AuthErrorCode.AUTH_TOKEN_INVALID));
    }

    @Test
    void tokensIssuedAfterRevokeAllWorkAgain() {
        tokenService.issue(5L, Audience.ADMIN, "ADMIN");
        tokenService.revokeAll(Audience.ADMIN, 5L);

        TokenService.IssuedTokens fresh = tokenService.issue(5L, Audience.ADMIN, "ADMIN");

        assertThat(tokenService.verifyAccess(fresh.accessToken()).subjectId()).isEqualTo(5L);
    }

    @Test
    void revokeAllDropsEveryRefreshSessionOfThatSubject() {
        TokenService.IssuedTokens first = tokenService.issue(8L, Audience.ADMIN, "ADMIN");
        TokenService.IssuedTokens second = tokenService.issue(8L, Audience.ADMIN, "ADMIN");

        tokenService.revokeAll(Audience.ADMIN, 8L);

        assertThat(values).doesNotContainKey(refreshKeyOf(first.refreshToken()));
        assertThat(values).doesNotContainKey(refreshKeyOf(second.refreshToken()));
        assertThat(values).containsKey("sky:auth:epoch:admin:8");
    }

    @Test
    void revokeAllToleratesEmptySubjectSet() {
        tokenService.revokeAll(Audience.CUSTOMER, 999L);

        assertThat(values).containsKey("sky:auth:epoch:customer:999");
    }

    /** 世代号被写成非数字时按 0 处理,不能让一次脏数据把整条链路打挂。 */
    @Test
    void nonNumericEpochIsTreatedAsZero() {
        values.put("sky:auth:epoch:admin:6", "not-a-number");

        TokenService.IssuedTokens tokens = tokenService.issue(6L, Audience.ADMIN, "ADMIN");

        assertThat(tokenService.verifyAccess(tokens.accessToken()).subjectId()).isEqualTo(6L);
    }

    // ---------------------------------------------------------------- refresh 轮换与登出

    @Test
    void rotateIssuesNewPairAndInvalidatesTheOldRefreshToken() {
        TokenService.IssuedTokens first = tokenService.issue(3L, Audience.ADMIN, "ADMIN");

        TokenService.IssuedTokens rotated = tokenService.rotate(first.refreshToken());

        assertThat(rotated.refreshToken()).isNotEqualTo(first.refreshToken());
        assertThat(tokenService.verifyAccess(rotated.accessToken()).subjectId()).isEqualTo(3L);
        assertThatThrownBy(() -> tokenService.rotate(first.refreshToken()))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(AuthErrorCode.AUTH_REFRESH_TOKEN_INVALID));
    }

    @Test
    void rotateRejectsAccessToken() {
        TokenService.IssuedTokens tokens = tokenService.issue(3L, Audience.ADMIN, "ADMIN");

        assertThatThrownBy(() -> tokenService.rotate(tokens.accessToken()))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(AuthErrorCode.AUTH_REFRESH_TOKEN_INVALID));
    }

    @Test
    void rotateRejectsGarbage() {
        assertThatThrownBy(() -> tokenService.rotate("not-a-jwt"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(AuthErrorCode.AUTH_REFRESH_TOKEN_INVALID));
    }

    @Test
    void rotateReportsExpirySeparately() {
        assertThatThrownBy(() -> tokenService.rotate(
                manualToken("refresh", "ADMIN", "jti-r1", 0L, Instant.now().minusSeconds(5))))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(AuthErrorCode.AUTH_REFRESH_TOKEN_EXPIRED));
    }

    /** 会话内容损坏(或被人写脏)时必须拒绝续期,而不是抛出 500。 */
    @Test
    void rotateRejectsCorruptedSessionPayload() {
        TokenService.IssuedTokens tokens = tokenService.issue(3L, Audience.ADMIN, "ADMIN");
        values.put(refreshKeyOf(tokens.refreshToken()), "not-json");

        assertThatThrownBy(() -> tokenService.rotate(tokens.refreshToken()))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(AuthErrorCode.AUTH_REFRESH_TOKEN_INVALID));
    }

    @Test
    void revokeRemovesRefreshSession() {
        TokenService.IssuedTokens tokens = tokenService.issue(4L, Audience.ADMIN, "ADMIN");

        tokenService.revoke(tokens.refreshToken());

        assertThatThrownBy(() -> tokenService.rotate(tokens.refreshToken()))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(AuthErrorCode.AUTH_REFRESH_TOKEN_INVALID));
    }

    /** 登出接口不该因为凭证已经无效而报错。 */
    @Test
    void revokeIgnoresMissingBlankOrInvalidTokens() {
        tokenService.revoke(null);
        tokenService.revoke("   ");
        tokenService.revoke("not-a-jwt");
        tokenService.revoke(manualToken("refresh", "ADMIN", "unknown-jti", 0L, Instant.now().minusSeconds(5)));

        assertThat(tokenService).isNotNull();
    }

    // ---------------------------------------------------------------- 辅助

    private static String refreshKeyOf(String refreshToken) {
        String jti = Jwts.parser().verifyWith(KEY).build().parseSignedClaims(refreshToken).getPayload().getId();
        return "sky:refresh:admin:" + jti;
    }

    /** 手工签发令牌:用于构造正常流程造不出来的形状(缺受众、缺世代、已过期)。 */
    private static String manualToken(String type, String audience, String jti, Long epoch, Instant expiry) {
        var builder = Jwts.builder()
                .issuer(ISSUER)
                .subject("1")
                .id(jti)
                .claim("role", "ADMIN")
                .claim("typ", type)
                .issuedAt(Date.from(Instant.now().minusSeconds(120)))
                .expiration(Date.from(expiry));
        if (epoch != null) {
            builder.claim("ep", epoch);
        }
        if (audience != null) {
            builder.audience().add(audience).and();
        }
        return builder.signWith(KEY).compact();
    }
}
