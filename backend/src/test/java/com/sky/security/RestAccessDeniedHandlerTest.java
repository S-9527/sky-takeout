package com.sky.security;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

class RestAccessDeniedHandlerTest {

    private final RestAccessDeniedHandler handler = new RestAccessDeniedHandler(new ObjectMapper());

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(CurrentPrincipal principal) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null));
    }

    private MockHttpServletResponse handle(String path) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.handle(new MockHttpServletRequest("GET", path), response, new AccessDeniedException("denied"));
        return response;
    }

    @Test
    void anonymousRequestIsTokenInvalid() throws Exception {
        MockHttpServletResponse response = handle("/api/v1/admin/employees");

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("AUTH_TOKEN_INVALID");
    }

    /** 顾客令牌打管理端:前端应该清令牌重登,所以不能笼统报"没有权限"。 */
    @Test
    void customerTokenOnAdminPathIsAudienceMismatch() throws Exception {
        authenticate(new CurrentPrincipal(1L, Audience.CUSTOMER, "CUSTOMER"));

        MockHttpServletResponse response = handle("/api/v1/admin/employees");

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("AUTH_AUDIENCE_MISMATCH");
    }

    @Test
    void adminTokenOnCustomerPathIsAudienceMismatch() throws Exception {
        authenticate(new CurrentPrincipal(1L, Audience.ADMIN, "STAFF"));

        MockHttpServletResponse response = handle("/api/v1/customer/profile");

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("AUTH_AUDIENCE_MISMATCH");
    }

    /** 同一个受众、只是角色不够(STAFF 打超管接口):前端应该隐藏入口,而不是清令牌。 */
    @Test
    void staffOnAdminOnlyPathIsPermissionDenied() throws Exception {
        authenticate(new CurrentPrincipal(1L, Audience.ADMIN, "STAFF"));

        MockHttpServletResponse response = handle("/api/v1/admin/employees");

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("AUTH_PERMISSION_DENIED");
    }

    @Test
    void nonApiPathFallsBackToPermissionDenied() throws Exception {
        authenticate(new CurrentPrincipal(1L, Audience.ADMIN, "ADMIN"));

        MockHttpServletResponse response = handle("/files/whatever.png");

        assertThat(response.getContentAsString()).contains("AUTH_PERMISSION_DENIED");
    }
}
