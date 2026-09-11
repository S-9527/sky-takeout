package com.sky.identity.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;

import com.sky.common.domain.EnableStatus;
import com.sky.common.domain.PageResponse;
import com.sky.common.error.BusinessException;
import com.sky.common.error.CommonErrorCode;
import com.sky.identity.api.dto.CustomerProfileUpdateRequest;
import com.sky.identity.api.dto.CustomerWechatLoginRequest;
import com.sky.identity.api.dto.EmployeeCreateRequest;
import com.sky.identity.api.dto.EmployeeLoginRequest;
import com.sky.identity.api.dto.EmployeeUpdateRequest;
import com.sky.identity.api.dto.RefreshTokenRequest;
import com.sky.identity.domain.Customer;
import com.sky.identity.domain.Employee;
import com.sky.identity.domain.EmployeeRole;
import com.sky.identity.service.CustomerService;
import com.sky.identity.service.EmployeeService;
import com.sky.security.Audience;
import com.sky.security.CurrentPrincipal;
import com.sky.security.TokenPairResponse;
import com.sky.security.TokenService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 控制器只做参数解析与 DTO 映射,但这两件事也会错,所以一样要测。 */
class IdentityControllerTest {

    private static final TokenService.IssuedTokens TOKENS =
            new TokenService.IssuedTokens("access", 7200, "refresh", 604800);

    private final EmployeeService employeeService = mock(EmployeeService.class);
    private final CustomerService customerService = mock(CustomerService.class);
    private final TokenService tokenService = mock(TokenService.class);

    private final EmployeeController employeeController = new EmployeeController(employeeService);
    private final EmployeeAuthController employeeAuthController =
            new EmployeeAuthController(employeeService, tokenService);
    private final CustomerAuthController customerAuthController =
            new CustomerAuthController(customerService, tokenService);
    private final CustomerProfileController customerProfileController =
            new CustomerProfileController(customerService);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private static void actingAs(CurrentPrincipal principal) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null));
    }

    private static Employee employee() {
        Employee employee = new Employee();
        employee.setId(1L);
        employee.setUsername("admin");
        employee.setName("管理员");
        employee.setPhone("13800000001");
        employee.setRole(EmployeeRole.ADMIN);
        employee.setStatus(EnableStatus.ENABLED);
        employee.setLastLoginAt(LocalDateTime.of(2026, 1, 2, 3, 4, 5));
        employee.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        employee.setUpdatedAt(LocalDateTime.of(2026, 1, 2, 0, 0));
        employee.setCreatedBy(0L);
        employee.setUpdatedBy(1L);
        return employee;
    }

    private static Customer customer() {
        Customer customer = new Customer();
        customer.setId(9L);
        customer.setOpenid("openid-9");
        customer.setNickname("小明");
        customer.setStatus(EnableStatus.ENABLED);
        customer.setLastLoginAt(LocalDateTime.of(2026, 1, 2, 3, 4, 5));
        return customer;
    }

    // ---------------------------------------------------------------- 员工管理

    @Test
    void pageParsesSortAndMapsEntitiesToDtos() {
        when(employeeService.page(any(), any(), any(), any(), any()))
                .thenReturn(PageResponse.of(List.of(employee()), 1, 20, 1));

        PageResponse<?> result = employeeController.page(
                1, 20, "username,asc", "管理", 1, EmployeeRole.ADMIN);

        assertThat(result.records()).hasSize(1);
        assertThat(result.total()).isEqualTo(1);
    }

    @Test
    void pageRejectsSortFieldOutsideWhitelistBeforeCallingService() {
        assertThatThrownBy(() -> employeeController.page(1, 20, "passwordHash,asc", null, null, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(CommonErrorCode.COMMON_SORT_FIELD_NOT_ALLOWED));
    }

    @Test
    void createPassesAllFieldsToService() {
        when(employeeService.create(any(), any(), any(), any(), any())).thenReturn(employee());

        assertThat(employeeController.create(
                new EmployeeCreateRequest("newbie", "secret123", "新人", "13900000000", EmployeeRole.STAFF)).username())
                .isEqualTo("admin");

        verify(employeeService).create("newbie", "secret123", "新人", "13900000000", EmployeeRole.STAFF);
    }

    @Test
    void getByIdReturnsMappedEmployee() {
        when(employeeService.requireById(1L)).thenReturn(employee());

        assertThat(employeeController.getById(1L).id()).isEqualTo(1L);
        assertThat(employeeController.getById(1L).role()).isEqualTo("ADMIN");
    }

    @Test
    void updatePassesAllFieldsToService() {
        when(employeeService.update(anyLong(), any(), any(), any(), any())).thenReturn(employee());

        employeeController.update(1L, new EmployeeUpdateRequest("管理员", "13800000001", EmployeeRole.ADMIN, 1));

        verify(employeeService).update(1L, "管理员", "13800000001", EmployeeRole.ADMIN, 1);
    }

    @Test
    void changeStatusMapsIntegerToEnum() {
        employeeController.changeStatus(2L, new com.sky.common.web.StatusPatchRequest(0));

        verify(employeeService).setStatus(2L, EnableStatus.DISABLED);
    }

    // ---------------------------------------------------------------- 员工认证

    @Test
    void loginReturnsTokenPairWithoutProfile() {
        when(employeeService.login("admin", "123456"))
                .thenReturn(new EmployeeService.LoginResult(employee(), TOKENS));

        TokenPairResponse response = employeeAuthController.login(new EmployeeLoginRequest("admin", "123456"));

        assertThat(response.accessToken()).isEqualTo("access");
        assertThat(response.refreshToken()).isEqualTo("refresh");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(7200);
    }

    @Test
    void meReadsEmployeeFromTokenSubject() {
        actingAs(new CurrentPrincipal(1L, Audience.ADMIN, "ADMIN"));
        when(employeeService.requireById(1L)).thenReturn(employee());

        assertThat(employeeAuthController.me().username()).isEqualTo("admin");
    }

    @Test
    void changePasswordUsesCurrentPrincipal() {
        actingAs(new CurrentPrincipal(1L, Audience.ADMIN, "ADMIN"));

        employeeAuthController.changePassword(
                new com.sky.identity.api.dto.ChangePasswordRequest("old-pass", "new-pass"));

        verify(employeeService).changePassword(1L, "old-pass", "new-pass");
    }

    @Test
    void refreshRotatesRefreshToken() {
        when(tokenService.rotate("refresh")).thenReturn(TOKENS);

        assertThat(employeeAuthController.refresh(new RefreshTokenRequest("refresh")).accessToken())
                .isEqualTo("access");
    }

    @Test
    void logoutRevokesRefreshToken() {
        employeeAuthController.logout(new RefreshTokenRequest("refresh"));

        verify(tokenService).revoke("refresh");
    }

    // ---------------------------------------------------------------- 顾客认证与资料

    @Test
    void wechatLoginReturnsTokenPair() {
        when(customerService.loginByWechat("code-1", "小明", "http://avatar/1.png"))
                .thenReturn(new CustomerService.LoginResult(customer(), TOKENS));

        TokenPairResponse response = customerAuthController.wechatLogin(
                new CustomerWechatLoginRequest("code-1", "小明", "http://avatar/1.png"));

        assertThat(response.accessToken()).isEqualTo("access");
    }

    @Test
    void customerRefreshAndLogoutDelegateToTokenService() {
        when(tokenService.rotate("refresh")).thenReturn(TOKENS);

        assertThat(customerAuthController.refresh(new RefreshTokenRequest("refresh")).refreshToken())
                .isEqualTo("refresh");
        customerAuthController.logout(new RefreshTokenRequest("refresh"));
        verify(tokenService).revoke("refresh");
    }

    @Test
    void profileGetUsesTokenSubjectNotPathVariable() {
        actingAs(new CurrentPrincipal(9L, Audience.CUSTOMER, "CUSTOMER"));
        when(customerService.requireById(9L)).thenReturn(customer());

        assertThat(customerProfileController.get().id()).isEqualTo(9L);
        // openid 是内部身份标识,不能出现在响应里
        assertThat(customerProfileController.get().toString()).doesNotContain("openid-9");
    }

    @Test
    void profileUpdateUsesTokenSubject() {
        actingAs(new CurrentPrincipal(9L, Audience.CUSTOMER, "CUSTOMER"));
        when(customerService.updateProfile(eq(9L), any(), any(), any())).thenReturn(customer());

        customerProfileController.update(new CustomerProfileUpdateRequest("小明", null, "13900000000"));

        verify(customerService).updateProfile(9L, "小明", null, "13900000000");
    }
}
