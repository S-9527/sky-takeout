package com.sky.identity.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import com.sky.common.domain.EnableStatus;
import com.sky.common.domain.PageQuery;
import com.sky.common.domain.PageResponse;
import com.sky.common.domain.SortSpec;
import com.sky.common.error.BusinessException;
import com.sky.common.error.CommonErrorCode;
import com.sky.identity.domain.Employee;
import com.sky.identity.domain.EmployeeRole;
import com.sky.identity.domain.IdentityErrorCode;
import com.sky.identity.mapper.EmployeeMapper;
import com.sky.security.Audience;
import com.sky.security.AuthErrorCode;
import com.sky.security.CurrentPrincipal;
import com.sky.security.TokenService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmployeeServiceTest {

    private final EmployeeMapper employeeMapper = mock(EmployeeMapper.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final TokenService tokenService = mock(TokenService.class);

    private final EmployeeService employeeService =
            new EmployeeService(employeeMapper, passwordEncoder, tokenService);

    @BeforeEach
    void defaultTokenPair() {
        when(tokenService.issue(anyLong(), any(), any()))
                .thenReturn(new TokenService.IssuedTokens("access", 7200, "refresh", 604800));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private static Employee employee(long id, EmployeeRole role, EnableStatus status) {
        Employee employee = new Employee();
        employee.setId(id);
        employee.setUsername("user" + id);
        employee.setPasswordHash("hash");
        employee.setName("员工" + id);
        employee.setPhone("13800000000");
        employee.setRole(role);
        employee.setStatus(status);
        return employee;
    }

    private static void actingAs(long employeeId) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new CurrentPrincipal(employeeId, Audience.ADMIN, "ADMIN"), null));
    }

    // ---------------------------------------------------------------- 登录

    @Test
    void loginIssuesTokensAndTouchesLastLoginAt() {
        Employee stored = employee(1L, EmployeeRole.ADMIN, EnableStatus.ENABLED);
        when(employeeMapper.selectOne(any())).thenReturn(stored);
        when(passwordEncoder.matches("123456", "hash")).thenReturn(true);

        EmployeeService.LoginResult result = employeeService.login("admin", "123456");

        assertThat(result.employee()).isSameAs(stored);
        assertThat(result.tokens().accessToken()).isEqualTo("access");
        verify(tokenService).issue(1L, Audience.ADMIN, "ADMIN");
        ArgumentCaptor<Employee> touch = ArgumentCaptor.forClass(Employee.class);
        verify(employeeMapper).updateById(touch.capture());
        assertThat(touch.getValue().getLastLoginAt()).isNotNull();
    }

    /** 用户名不存在与密码错误必须无法区分,否则接口就是个账号枚举器。 */
    @Test
    void unknownUsernameAndWrongPasswordShareTheSameErrorCode() {
        when(employeeMapper.selectOne(any())).thenReturn(null);
        assertThatThrownBy(() -> employeeService.login("nobody", "x"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(AuthErrorCode.AUTH_BAD_CREDENTIALS));

        when(employeeMapper.selectOne(any())).thenReturn(employee(1L, EmployeeRole.ADMIN, EnableStatus.ENABLED));
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);
        assertThatThrownBy(() -> employeeService.login("admin", "wrong"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(AuthErrorCode.AUTH_BAD_CREDENTIALS));
    }

    @Test
    void disabledEmployeeCannotLogin() {
        when(employeeMapper.selectOne(any())).thenReturn(employee(1L, EmployeeRole.STAFF, EnableStatus.DISABLED));
        when(passwordEncoder.matches("123456", "hash")).thenReturn(true);

        assertThatThrownBy(() -> employeeService.login("user1", "123456"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(IdentityErrorCode.EMPLOYEE_DISABLED));
        verify(tokenService, never()).issue(anyLong(), any(), any());
    }

    // ---------------------------------------------------------------- 查询

    @Test
    void requireByIdReturnsEmployeeOr404() {
        Employee stored = employee(2L, EmployeeRole.STAFF, EnableStatus.ENABLED);
        when(employeeMapper.selectById(2L)).thenReturn(stored);
        assertThat(employeeService.requireById(2L)).isSameAs(stored);

        when(employeeMapper.selectById(404L)).thenReturn(null);
        assertThatThrownBy(() -> employeeService.requireById(404L))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(IdentityErrorCode.EMPLOYEE_NOT_FOUND));
    }

    @Test
    void pageAppliesFiltersAndReturnsMappedMetadata() {
        Page<Employee> page = new Page<>(2, 10);
        page.setRecords(List.of(employee(1L, EmployeeRole.STAFF, EnableStatus.ENABLED)));
        page.setTotal(21);
        when(employeeMapper.selectPage(any(), any())).thenReturn(page);

        PageResponse<Employee> result = employeeService.page(
                "张", 1, EmployeeRole.STAFF, PageQuery.of(2, 10),
                SortSpec.parse("username,asc", EmployeeService.SORT_WHITELIST, "createdAt", true));

        assertThat(result.records()).hasSize(1);
        assertThat(result.page()).isEqualTo(2);
        assertThat(result.pageSize()).isEqualTo(10);
        assertThat(result.total()).isEqualTo(21);
    }

    /** 白名单之外的字段到了这里必须仍然被拒,不能因为"控制器已校验"就默默放行。 */
    @Test
    void pageRejectsSortFieldOutsideWhitelist() {
        assertThatThrownBy(() -> employeeService.page(
                null, null, null, PageQuery.of(1, 10), new SortSpec("passwordHash", false)))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(CommonErrorCode.COMMON_SORT_FIELD_NOT_ALLOWED));
    }

    @Test
    void pageSupportsEveryWhitelistedSortField() {
        Page<Employee> page = new Page<>(1, 10);
        page.setRecords(List.of());
        when(employeeMapper.selectPage(any(), any())).thenReturn(page);

        for (String field : EmployeeService.SORT_WHITELIST) {
            employeeService.page(null, null, null, PageQuery.of(1, 10), new SortSpec(field, true));
        }

        assertThat(EmployeeService.SORT_WHITELIST).containsExactlyInAnyOrder("createdAt", "username", "lastLoginAt");
    }

    // ---------------------------------------------------------------- 新增

    @Test
    void createEncodesPasswordAndStartsEnabled() {
        when(employeeMapper.exists(any())).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("encoded");

        Employee created = employeeService.create("newbie", "secret123", "新人", "13900000000", EmployeeRole.STAFF);

        assertThat(created.getPasswordHash()).isEqualTo("encoded");
        assertThat(created.getStatus()).isEqualTo(EnableStatus.ENABLED);
        assertThat(created.getRole()).isEqualTo(EmployeeRole.STAFF);
        verify(employeeMapper).insert(created);
    }

    @Test
    void createRejectsDuplicateUsername() {
        when(employeeMapper.exists(any())).thenReturn(true);

        assertThatThrownBy(() -> employeeService.create("admin", "secret123", "重复", null, EmployeeRole.STAFF))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(IdentityErrorCode.EMPLOYEE_USERNAME_TAKEN));
        verify(employeeMapper, never()).insert(any(Employee.class));
    }

    // ---------------------------------------------------------------- 编辑与自我保护

    @Test
    void updateRejectsChangingOwnRole() {
        actingAs(1L);
        when(employeeMapper.selectById(1L)).thenReturn(employee(1L, EmployeeRole.ADMIN, EnableStatus.ENABLED));

        assertThatThrownBy(() -> employeeService.update(1L, "管理员", null, EmployeeRole.STAFF, 1))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(IdentityErrorCode.EMPLOYEE_SELF_ROLE_CHANGE));
        verify(employeeMapper, never()).updateById(any(Employee.class));
    }

    @Test
    void updateRejectsDisablingSelf() {
        actingAs(1L);
        when(employeeMapper.selectById(1L)).thenReturn(employee(1L, EmployeeRole.ADMIN, EnableStatus.ENABLED));

        assertThatThrownBy(() -> employeeService.update(1L, "管理员", null, EmployeeRole.ADMIN, 0))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(IdentityErrorCode.EMPLOYEE_SELF_DISABLE));
    }

    @Test
    void updateDisablingSomeoneElseRevokesTheirSessions() {
        actingAs(1L);
        when(employeeMapper.selectById(2L)).thenReturn(employee(2L, EmployeeRole.STAFF, EnableStatus.ENABLED));

        employeeService.update(2L, "张三", "13800000000", EmployeeRole.STAFF, 0);

        verify(tokenService).revokeAll(Audience.ADMIN, 2L);
    }

    @Test
    void updateKeepingEmployeeEnabledDoesNotRevokeSessions() {
        actingAs(1L);
        when(employeeMapper.selectById(2L)).thenReturn(employee(2L, EmployeeRole.STAFF, EnableStatus.ENABLED));

        employeeService.update(2L, "张三", "13800000000", EmployeeRole.STAFF, 1);

        verify(tokenService, never()).revokeAll(any(), anyLong());
    }

    @Test
    void updateRejectsUnknownEmployee() {
        when(employeeMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> employeeService.update(99L, "谁", null, EmployeeRole.STAFF, 1))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(IdentityErrorCode.EMPLOYEE_NOT_FOUND));
    }

    // ---------------------------------------------------------------- 启停用

    @Test
    void setStatusDisablingSomeoneElseRevokesSessions() {
        actingAs(1L);
        when(employeeMapper.selectById(3L)).thenReturn(employee(3L, EmployeeRole.STAFF, EnableStatus.ENABLED));

        employeeService.setStatus(3L, EnableStatus.DISABLED);

        verify(tokenService).revokeAll(Audience.ADMIN, 3L);
    }

    @Test
    void setStatusRejectsDisablingSelf() {
        actingAs(1L);
        when(employeeMapper.selectById(1L)).thenReturn(employee(1L, EmployeeRole.ADMIN, EnableStatus.ENABLED));

        assertThatThrownBy(() -> employeeService.setStatus(1L, EnableStatus.DISABLED))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(IdentityErrorCode.EMPLOYEE_SELF_DISABLE));
        verify(employeeMapper, never()).updateById(any(Employee.class));
    }

    // ---------------------------------------------------------------- 改密

    @Test
    void changePasswordVerifiesOldPasswordAndRevokesSessions() {
        when(employeeMapper.selectById(1L)).thenReturn(employee(1L, EmployeeRole.ADMIN, EnableStatus.ENABLED));
        when(passwordEncoder.matches("old-pass", "hash")).thenReturn(true);
        when(passwordEncoder.matches("new-pass", "hash")).thenReturn(false);
        when(passwordEncoder.encode("new-pass")).thenReturn("new-hash");

        employeeService.changePassword(1L, "old-pass", "new-pass");

        ArgumentCaptor<Employee> update = ArgumentCaptor.forClass(Employee.class);
        verify(employeeMapper).updateById(update.capture());
        assertThat(update.getValue().getPasswordHash()).isEqualTo("new-hash");
        verify(tokenService).revokeAll(Audience.ADMIN, 1L);
    }

    @Test
    void changePasswordRejectsWrongOldPassword() {
        when(employeeMapper.selectById(1L)).thenReturn(employee(1L, EmployeeRole.ADMIN, EnableStatus.ENABLED));
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        assertThatThrownBy(() -> employeeService.changePassword(1L, "wrong", "new-pass"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(AuthErrorCode.AUTH_BAD_CREDENTIALS));
        verify(employeeMapper, never()).updateById(any(Employee.class));
    }

    @Test
    void changePasswordRejectsReusingTheSamePassword() {
        when(employeeMapper.selectById(1L)).thenReturn(employee(1L, EmployeeRole.ADMIN, EnableStatus.ENABLED));
        when(passwordEncoder.matches("same-pass", "hash")).thenReturn(true);

        assertThatThrownBy(() -> employeeService.changePassword(1L, "same-pass", "same-pass"))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.errorCode()).isEqualTo(CommonErrorCode.COMMON_VALIDATION_FAILED);
                    assertThat(ex.details()).isNotEmpty();
                });
    }

    @Test
    void changePasswordRejectsUnknownEmployee() {
        when(employeeMapper.selectById(77L)).thenReturn(null);

        assertThatThrownBy(() -> employeeService.changePassword(77L, "old", "new"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(IdentityErrorCode.EMPLOYEE_NOT_FOUND));
    }

    @Test
    void loginWithoutAnyAuthenticationContextStillWorks() {
        // currentEmployeeId() 读不到主体时必须返回 null,而不是抛异常
        when(employeeMapper.selectOne(any())).thenReturn(employee(9L, EmployeeRole.ADMIN, EnableStatus.ENABLED));
        when(passwordEncoder.matches(eq("123456"), any())).thenReturn(true);

        assertThat(employeeService.login("user9", "123456").employee().getId()).isEqualTo(9L);
    }
}
