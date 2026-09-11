package com.sky.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.concurrent.atomic.AtomicReference;

import jakarta.servlet.FilterChain;

import com.sky.common.error.BusinessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    private final TokenService tokenService = mock(TokenService.class);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(tokenService);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private static MockHttpServletRequest requestWithBearer(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/admin/auth/me");
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    @Test
    void validTokenPopulatesSecurityContextForTheRequest() throws Exception {
        when(tokenService.verifyAccess("good")).thenReturn(new TokenService.TokenPayload(7L, Audience.ADMIN, "ADMIN"));
        AtomicReference<CurrentPrincipal> seenByController = new AtomicReference<>();
        FilterChain chain = (req, res) -> seenByController.set(CurrentPrincipal.current());

        filter.doFilter(requestWithBearer("good"), new MockHttpServletResponse(), chain);

        assertThat(seenByController.get()).isEqualTo(new CurrentPrincipal(7L, Audience.ADMIN, "ADMIN"));
        // 线程池会复用线程:请求结束后必须清掉安全上下文
        assertThat(CurrentPrincipal.current()).isNull();
    }

    /** 令牌无效不直接写响应,而是记下错误码让 EntryPoint 统一输出——放行接口不受影响。 */
    @Test
    void invalidTokenIsRecordedAsAttributeAndRequestStillProceeds() throws Exception {
        when(tokenService.verifyAccess("bad"))
                .thenThrow(new BusinessException(AuthErrorCode.AUTH_TOKEN_EXPIRED));
        MockHttpServletRequest request = requestWithBearer("bad");
        AtomicReference<Object> recorded = new AtomicReference<>();
        FilterChain chain = (req, res) -> {
            recorded.set(((MockHttpServletRequest) req).getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE));
            return;
        };

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(recorded.get()).isEqualTo(AuthErrorCode.AUTH_TOKEN_EXPIRED);
        assertThat(CurrentPrincipal.current()).isNull();
    }

    @Test
    void requestWithoutAuthorizationHeaderSkipsTokenParsing() throws Exception {
        AtomicReference<CurrentPrincipal> seenByController = new AtomicReference<>();
        FilterChain chain = (req, res) -> seenByController.set(CurrentPrincipal.current());

        filter.doFilter(new MockHttpServletRequest("GET", "/api/v1/admin/auth/login"),
                new MockHttpServletResponse(), chain);

        assertThat(seenByController.get()).isNull();
        verifyNoInteractions(tokenService);
    }

    @Test
    void nonBearerAuthorizationHeaderIsIgnored() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/x");
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
        });

        verifyNoInteractions(tokenService);
    }

    @Test
    void bearerTokenIsTrimmedBeforeVerification() throws Exception {
        when(tokenService.verifyAccess("good")).thenReturn(new TokenService.TokenPayload(7L, Audience.ADMIN, "STAFF"));

        filter.doFilter(requestWithBearer("  good  "), new MockHttpServletResponse(), (req, res) -> {
        });

        verify(tokenService).verifyAccess("good");
    }
}
