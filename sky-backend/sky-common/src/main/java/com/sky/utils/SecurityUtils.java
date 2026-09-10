package com.sky.utils;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 当前登录用户工具类：从 {@link SecurityContextHolder} 读取认证主体(用户ID)。
 *
 * <p>用户ID 由认证过滤器在登录鉴权后写入 principal，无需再依赖 ThreadLocal 手动传递。</p>
 */
public class SecurityUtils {

    private SecurityUtils() {
    }

    /**
     * 获取当前登录用户 ID。
     *
     * @return 已登录用户 ID；未登录或主体类型不匹配时返回 null
     */
    public static Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        return principal instanceof Long ? (Long) principal : null;
    }
}