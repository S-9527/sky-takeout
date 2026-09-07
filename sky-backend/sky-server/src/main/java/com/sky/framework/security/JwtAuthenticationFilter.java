package com.sky.framework.security;

import com.sky.constant.JwtClaimsConstant;
import com.sky.context.BaseContext;
import com.sky.properties.JwtProperties;
import com.sky.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 认证过滤器：解析管理端/用户端令牌，写入 SecurityContext 与 BaseContext
 */
@Component
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private JwtProperties jwtProperties;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String adminToken = request.getHeader(jwtProperties.getAdminTokenName());
        String userToken = request.getHeader(jwtProperties.getUserTokenName());

        // 尝试解析管理端令牌
        if (adminToken != null && !adminToken.isEmpty()
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            authenticate(adminToken, jwtProperties.getAdminSecretKey(),
                    JwtClaimsConstant.EMP_ID, "ROLE_ADMIN", request);
        }

        // 尝试解析用户端令牌
        if (userToken != null && !userToken.isEmpty()
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            authenticate(userToken, jwtProperties.getUserSecretKey(),
                    JwtClaimsConstant.USER_ID, "ROLE_USER", request);
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(String token, String secretKey, String claimKey,
                              String role, HttpServletRequest request) {
        try {
            Claims claims = JwtUtil.parseJWT(secretKey, token);
            Long id = Long.valueOf(claims.get(claimKey).toString());
            BaseContext.setCurrentId(id);

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(id, null,
                            List.of(new SimpleGrantedAuthority(role)));
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (Exception ex) {
            // 解析失败：不设置认证信息，交由 Security 的入口点返回 401
            log.warn("JWT 校验失败: {}", ex.getMessage());
        }
    }
}
