package com.sky.auth.filter;

import com.sky.auth.token.JwtTokenBlacklistService;
import com.sky.auth.token.JwtTokenService;
import com.sky.auth.token.TokenPurpose;
import com.sky.auth.token.TokenType;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 认证过滤器：解析 {@code Authorization: Bearer <token>} 令牌，
 * 校验签名、iss/aud/用途与黑名单后写入 SecurityContext(principal=用户ID)。
 *
 * <p>按 {@link TokenType} 依次尝试验签(管理端 → 用户端)，命中后以枚举回查
 * 主键声明字段与角色，保证 claim/issuer/role 三处契约一致。</p>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenService jwtTokenService;
    private final JwtTokenBlacklistService blacklistService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            resolveAuthorization(request);
        }
        filterChain.doFilter(request, response);
    }

    private void resolveAuthorization(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            return;
        }

        for (TokenType type : TokenType.values()) {
            Claims claims = tryParse(token, type);
            if (claims != null) {
                authenticate(claims, token, type, request);
                return;
            }
        }
    }

    private Claims tryParse(String token, TokenType type) {
        try {
            return jwtTokenService.parseToken(token, type);
        } catch (Exception ex) {
            return null;
        }
    }

    private void authenticate(Claims claims, String token, TokenType type, HttpServletRequest request) {
        // 仅接受访问令牌：刷新令牌不能作为 Bearer 使用(无 token_type 声明的旧令牌同样失效)
        if (!TokenPurpose.ACCESS.matches(claims.get(TokenPurpose.CLAIM_KEY))) {
            log.warn("非访问令牌被拒: jti={}, type={}", claims.getId(), claims.get(TokenPurpose.CLAIM_KEY));
            return;
        }

        // 黑名单校验：已撤销的令牌视为未认证
        if (blacklistService.isRevoked(claims.getId())) {
            log.warn("JWT 已被撤销: jti={}", claims.getId());
            return;
        }

        Long id = Long.valueOf(claims.get(type.getClaimKey()).toString());

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(id, null,
                        List.of(new SimpleGrantedAuthority(type.getSpringRole())));
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}