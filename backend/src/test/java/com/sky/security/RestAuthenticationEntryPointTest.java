package com.sky.security;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;

import static org.assertj.core.api.Assertions.assertThat;

class RestAuthenticationEntryPointTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestAuthenticationEntryPoint entryPoint = new RestAuthenticationEntryPoint(objectMapper);

    /** 没有过滤器记下的原因时,兜底是"令牌无效",而不是空 body。 */
    @Test
    void defaultsToTokenInvalidWhenFilterRecordedNothing() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(new MockHttpServletRequest(), response, new InsufficientAuthenticationException("no token"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).contains("application/json");
        assertThat(response.getContentAsString()).contains("AUTH_TOKEN_INVALID");
    }

    /** 过滤器会把真实原因(过期 ≠ 无效)记在请求属性上,入口点必须原样透出。 */
    @Test
    void prefersErrorCodeRecordedByJwtFilter() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE, AuthErrorCode.AUTH_TOKEN_EXPIRED);
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(request, response, new InsufficientAuthenticationException("expired"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("AUTH_TOKEN_EXPIRED");
    }
}
