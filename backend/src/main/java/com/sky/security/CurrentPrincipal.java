package com.sky.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.sky.common.error.BusinessException;

/**
 * 当前登录主体。
 *
 * <p>业务代码通过 {@link #current()} / {@link #require()} 获取当前用户,不要把它当方法参数层层传递,
 * 也不要直接读 {@code SecurityContextHolder}。
 *
 * @param id       员工 id 或顾客 id
 * @param audience 令牌受众
 * @param role     管理端为 ADMIN / STAFF;顾客端为 CUSTOMER
 */
public record CurrentPrincipal(Long id, Audience audience, String role) {

    /** Spring Security 角色名前缀。权限串是 {@code ROLE_} + {@code role},与受众无关。 */
    public static final String ROLE_PREFIX = "ROLE_";

    /** 本主体在 Spring Security 中的权限串,如 {@code ROLE_ADMIN}、{@code ROLE_CUSTOMER}。 */
    public String authority() {
        return ROLE_PREFIX + role;
    }

    public boolean isAdmin() {
        return audience == Audience.ADMIN && "ADMIN".equals(role);
    }

    public boolean isCustomer() {
        return audience == Audience.CUSTOMER;
    }

    /** 当前主体,未认证时返回 null。 */
    public static CurrentPrincipal current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CurrentPrincipal principal)) {
            return null;
        }
        return principal;
    }

    /** 当前主体,未认证时抛 401。 */
    public static CurrentPrincipal require() {
        CurrentPrincipal principal = current();
        if (principal == null) {
            throw new BusinessException(AuthErrorCode.AUTH_TOKEN_INVALID);
        }
        return principal;
    }

    /** 当前顾客 id,非顾客身份时抛 401。 */
    public static Long requireCustomerId() {
        CurrentPrincipal principal = require();
        if (!principal.isCustomer()) {
            throw new BusinessException(AuthErrorCode.AUTH_TOKEN_INVALID);
        }
        return principal.id();
    }
}
