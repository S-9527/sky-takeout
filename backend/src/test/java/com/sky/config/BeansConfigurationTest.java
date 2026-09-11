package com.sky.config;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

import com.sky.identity.domain.Employee;
import com.sky.security.Audience;
import com.sky.security.CurrentPrincipal;
import com.sky.security.TokenPairResponse;
import com.sky.security.TokenService;

import static org.assertj.core.api.Assertions.assertThat;

/** 装配层的小测试:这些 Bean 的配置错了,表现会是"运行时才发现",所以值得钉住。 */
class BeansConfigurationTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void paginationInterceptorIsRegistered() {
        MybatisPlusInterceptor interceptor = new MybatisPlusConfig().mybatisPlusInterceptor();

        assertThat(interceptor.getInterceptors())
                .anyMatch(inner -> inner instanceof PaginationInnerInterceptor);
    }

    @Test
    void auditingHandlerIsExposedAsBean() {
        assertThat(new MybatisPlusConfig().auditingMetaObjectHandler()).isNotNull();
        assertThat(AuditingMetaObjectHandler.SYSTEM_ACTOR_ID).isZero();
    }

    @Test
    void passwordEncoderUsesBCrypt() {
        PasswordEncoder encoder = securityConfig().passwordEncoder();

        String encoded = encoder.encode("123456");

        assertThat(encoded).startsWith("$2");
        assertThat(encoder.matches("123456", encoded)).isTrue();
        assertThat(encoder.matches("wrong", encoded)).isFalse();
    }

    @Test
    void corsAllowsConfiguredOriginPatternsForEveryPath() {
        UrlBasedCorsConfigurationSource source =
                (UrlBasedCorsConfigurationSource) securityConfig().corsConfigurationSource();

        CorsConfiguration configuration = source.getCorsConfiguration(
                new MockHttpServletRequest("GET", "/api/v1/admin/shop/status"));

        assertThat(configuration).isNotNull();
        assertThat(configuration.getAllowedOriginPatterns()).containsExactly("http://localhost:*");
        assertThat(configuration.getExposedHeaders()).contains("X-Trace-Id");
        assertThat(configuration.getAllowCredentials()).isTrue();
    }

    /** 审计字段:有登录主体时记主体 id,没有(定时任务)时记 0。 */
    @Test
    void auditingHandlerFillsCurrentPrincipalOnInsertAndUpdate() {
        registerTableInfo(Employee.class);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new CurrentPrincipal(12L, Audience.ADMIN, "ADMIN"), null));
        AuditingMetaObjectHandler handler = new AuditingMetaObjectHandler();
        Employee employee = new Employee();

        handler.insertFill(SystemMetaObject.forObject(employee));
        assertThat(employee.getCreatedBy()).isEqualTo(12L);
        assertThat(employee.getUpdatedBy()).isEqualTo(12L);

        Employee updated = new Employee();
        handler.updateFill(SystemMetaObject.forObject(updated));
        assertThat(updated.getUpdatedBy()).isEqualTo(12L);
        assertThat(updated.getCreatedBy()).isNull();
    }

    @Test
    void auditingHandlerFillsSystemActorWhenNoPrincipal() {
        registerTableInfo(Employee.class);
        AuditingMetaObjectHandler handler = new AuditingMetaObjectHandler();
        Employee employee = new Employee();

        handler.insertFill(SystemMetaObject.forObject(employee));

        assertThat(employee.getCreatedBy()).isEqualTo(AuditingMetaObjectHandler.SYSTEM_ACTOR_ID);
    }

    private static void registerTableInfo(Class<?> entityType) {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "test");
        assistant.setCurrentNamespace("test");
        TableInfoHelper.initTableInfo(assistant, entityType);
    }

    @Test
    void tokenPairResponseAlwaysReportsBearer() {
        TokenPairResponse response = TokenPairResponse.from(
                new TokenService.IssuedTokens("a", 1, "r", 2));

        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(1);
        assertThat(response.refreshExpiresIn()).isEqualTo(2);
    }

    private static SecurityConfig securityConfig() {
        WebProperties webProperties = new WebProperties(List.of("http://localhost:*"));
        return new SecurityConfig(null, null, null, webProperties);
    }
}
