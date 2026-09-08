package com.sky.framework.security;

import com.sky.constant.JwtClaimsConstant;
import com.sky.context.BaseContext;
import com.sky.token.JwtTokenBlacklistService;
import com.sky.token.JwtTokenService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import org.springframework.lang.NonNull;

/**
 * JWT 认证过滤器：解析 {@code Authorization: Bearer <token>} 令牌，
 * 校验签名与黑名单后写入 SecurityContext 与 BaseContext。
 *
 * <p>通过令牌中的 iss(admin/user)与角色声明区分管理端/用户端权限。</p>
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

        try {
            // 先按管理端密钥验签，再按用户端密钥验签，兜底区分角色
            Claims adminClaims = tryParseAdmin(token);
            if (adminClaims != null) {
                authenticate(adminClaims, token, request,
                        JwtClaimsConstant.EMP_ID, "ROLE_ADMIN");
                return;
            }
            Claims userClaims = jwtTokenService.parseUserToken(token);
            authenticate(userClaims, token, request,
                    JwtClaimsConstant.USER_ID, "ROLE_USER");
        } catch (Exception ex) {
            // 解析失败：不设置认证信息，交由 Security 的入口点返回 401
            log.warn("JWT 校验失败: {}", ex.getMessage());
        }
    }

    private Claims tryParseAdmin(String token) {
        try {
            return jwtTokenService.parseToken(token);
        } catch (Exception ex) {
            return null;
        }
    }

    private void authenticate(Claims claims, String token, HttpServletRequest request,
                              String claimKey, String role) {
        // 黑名单校验：已撤销的令牌视为未认证
        if (blacklistService.isRevoked(claims.getId())) {
            log.warn("JWT 已被撤销: jti={}", claims.getId());
            return;
        }

        Long id = Long.valueOf(claims.get(claimKey).toString());
        BaseContext.setCurrentId(id);

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(id, null,
                        List.of(new SimpleGrantedAuthority(role)));
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}