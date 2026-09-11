package com.sky.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

import com.sky.common.error.ErrorCode;
import com.sky.common.error.ErrorResponse;
import com.sky.common.web.TraceIdFilter;

/**
 * 已认证但无权限时的 403 响应。
 *
 * <p>这里区分两种被拒原因,因为它们的处置方式完全不同:
 * <ul>
 *   <li><b>受众不匹配</b>——顾客令牌打了管理端接口。前端应清掉令牌重新登录,
 *       而不是提示"没有权限"然后让用户干瞪眼。</li>
 *   <li><b>角色不足</b>——员工登录了,但不是 ADMIN。前端应隐藏对应入口。</li>
 * </ul>
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private static final String ADMIN_PATH_PREFIX = "/api/v1/admin";
    private static final String CUSTOMER_PATH_PREFIX = "/api/v1/customer";

    private final ObjectMapper objectMapper;

    public RestAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        writeError(response, resolveErrorCode(request));
    }

    private ErrorCode resolveErrorCode(HttpServletRequest request) {
        CurrentPrincipal principal = CurrentPrincipal.current();
        if (principal == null) {
            return AuthErrorCode.AUTH_TOKEN_INVALID;
        }
        String path = request.getRequestURI();
        boolean adminPath = path.startsWith(ADMIN_PATH_PREFIX);
        boolean customerPath = path.startsWith(CUSTOMER_PATH_PREFIX);
        if ((adminPath && principal.isCustomer()) || (customerPath && !principal.isCustomer())) {
            return AuthErrorCode.AUTH_AUDIENCE_MISMATCH;
        }
        return AuthErrorCode.AUTH_PERMISSION_DENIED;
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
