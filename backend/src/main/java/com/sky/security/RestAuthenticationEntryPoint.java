package com.sky.security;

import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

import com.sky.common.error.ErrorCode;
import com.sky.common.error.ErrorResponse;
import com.sky.common.web.TraceIdFilter;

/**
 * 未认证时的 401 响应。
 *
 * <p>Spring Security 默认返回一个空 body,前端拿不到可展示的信息,所以必须自己写。
 * 错误码优先取 {@link JwtAuthenticationFilter} 记下的真实原因(缺失 / 过期 / 无效)。
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        ErrorCode errorCode = (ErrorCode) request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE);
        if (errorCode == null) {
            errorCode = AuthErrorCode.AUTH_TOKEN_INVALID;
        }
        writeError(response, errorCode);
    }

    private void writeError(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.httpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ErrorResponse body = new ErrorResponse(
                errorCode.code(), errorCode.defaultMessage(), List.of(), TraceIdFilter.currentTraceId());
        objectMapper.writeValue(response.getWriter(), body);
    }
}
