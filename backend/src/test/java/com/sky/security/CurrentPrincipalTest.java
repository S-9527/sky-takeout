package com.sky.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.sky.common.error.BusinessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrentPrincipalTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private static void authenticate(CurrentPrincipal principal) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null));
    }

    @Test
    void currentIsNullWhenNothingIsAuthenticated() {
        assertThat(CurrentPrincipal.current()).isNull();
    }

    @Test
    void requireThrows401WhenNothingIsAuthenticated() {
        assertThatThrownBy(CurrentPrincipal::require)
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(AuthErrorCode.AUTH_TOKEN_INVALID));
    }

    @Test
    void authorityIsRolePrefixedRegardlessOfAudience() {
        assertThat(new CurrentPrincipal(1L, Audience.ADMIN, "ADMIN").authority()).isEqualTo("ROLE_ADMIN");
        assertThat(new CurrentPrincipal(2L, Audience.ADMIN, "STAFF").authority()).isEqualTo("ROLE_STAFF");
        assertThat(new CurrentPrincipal(3L, Audience.CUSTOMER, "CUSTOMER").authority()).isEqualTo("ROLE_CUSTOMER");
    }

    @Test
    void isAdminNeedsBothAdminAudienceAndAdminRole() {
        assertThat(new CurrentPrincipal(1L, Audience.ADMIN, "ADMIN").isAdmin()).isTrue();
        assertThat(new CurrentPrincipal(1L, Audience.ADMIN, "STAFF").isAdmin()).isFalse();
        // 顾客令牌即使 role 被伪造成 ADMIN,也不该被当成管理员
        assertThat(new CurrentPrincipal(1L, Audience.CUSTOMER, "ADMIN").isAdmin()).isFalse();
    }

    @Test
    void isCustomerOnlyDependsOnAudience() {
        assertThat(new CurrentPrincipal(9L, Audience.CUSTOMER, "CUSTOMER").isCustomer()).isTrue();
        assertThat(new CurrentPrincipal(9L, Audience.ADMIN, "STAFF").isCustomer()).isFalse();
    }

    @Test
    void requireCustomerIdReturnsCustomerId() {
        authenticate(new CurrentPrincipal(7L, Audience.CUSTOMER, "CUSTOMER"));

        assertThat(CurrentPrincipal.requireCustomerId()).isEqualTo(7L);
    }

    /** 员工令牌打了顾客端接口时,取顾客 id 必须是 401,而不是 0 或 null 之类的"幽灵身份"。 */
    @Test
    void requireCustomerIdRejectsNonCustomerPrincipals() {
        authenticate(new CurrentPrincipal(7L, Audience.ADMIN, "ADMIN"));

        assertThatThrownBy(CurrentPrincipal::requireCustomerId)
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(AuthErrorCode.AUTH_TOKEN_INVALID));
    }

    @Test
    void currentReadsPrincipalFromSecurityContext() {
        CurrentPrincipal principal = new CurrentPrincipal(5L, Audience.ADMIN, "STAFF");
        authenticate(principal);

        assertThat(CurrentPrincipal.current()).isEqualTo(principal);
        assertThat(CurrentPrincipal.require()).isEqualTo(principal);
    }
}
