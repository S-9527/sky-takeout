package com.sky.framework.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * 统一访问日志：一行一条记录请求方法与路径、耗时与响应状态，
 * 替代各 Controller 分散的入参日志。静态资源、接口文档与 WebSocket 不在记录范围。
 */
@Component
@Slf4j
public class AccessLogFilter extends OncePerRequestFilter {

    private static final Pattern EXCLUDED = Pattern.compile(
            "/(files|doc\\.html|swagger-ui|v3/api-docs|webjars|ws|favicon\\.ico|error)(/.*)?");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long start = System.currentTimeMillis();
        try {
            filterChain.doFilter(request, response);
        } finally {
            if (!EXCLUDED.matcher(request.getRequestURI()).matches()) {
                long elapsed = System.currentTimeMillis() - start;
                log.info("[ACCESS] {} {} -> {} {}ms client={}",
                        request.getMethod(),
                        request.getRequestURI(),
                        response.getStatus(),
                        elapsed,
                        clientIp(request));
            }
        }
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}