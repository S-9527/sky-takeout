package com.sky.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

import com.sky.common.error.BusinessException;

/**
 * 解析 {@code Authorization: Bearer <accessToken>},成功则把 {@link CurrentPrincipal} 放进安全上下文。
 *
 * <p>令牌无效时**不直接返回错误**,而是把错误码记在请求属性上,交给
 * {@link RestAuthenticationEntryPoint} 统一输出——这样放行接口(登录、回调)不受影响,
 * 而受保护接口能拿到精确的错误码(过期 ≠ 无效)。
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String AUTH_ERROR_ATTRIBUTE = "sky.authError";

    private static final String BEARER_PREFIX = "Bearer ";

    private final TokenService tokenService;

    public JwtAuthenticationFilter(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length()).trim();
            try {
                TokenService.TokenPayload payload = tokenService.verifyAccess(token);
                CurrentPrincipal principal = new CurrentPrincipal(payload.subjectId(), payload.audience(), payload.role());
                var authentication = new UsernamePasswordAuthenticationToken(
                        principal, null, List.of(new SimpleGrantedAuthority(principal.authority())));
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (BusinessException ex) {
                SecurityContextHolder.clearContext();
                request.setAttribute(AUTH_ERROR_ATTRIBUTE, ex.errorCode());
            }
        }

        try {
            chain.doFilter(request, response);
        } finally {
            // 线程池复用线程,必须显式清理,否则上一个请求的身份会泄漏给下一个
            SecurityContextHolder.clearContext();
        }
    }
}
