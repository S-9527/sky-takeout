package com.sky.identity.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sky.identity.api.dto.ChangePasswordRequest;
import com.sky.identity.api.dto.EmployeeLoginRequest;
import com.sky.identity.api.dto.EmployeeResponse;
import com.sky.identity.api.dto.RefreshTokenRequest;
import com.sky.identity.service.EmployeeService;
import com.sky.security.CurrentPrincipal;
import com.sky.security.TokenPairResponse;
import com.sky.security.TokenService;

/** 管理端认证:登录、登出、续期、改密、查当前员工。 */
@RestController
@RequestMapping("/api/v1/admin/auth")
public class EmployeeAuthController {

    private final EmployeeService employeeService;
    private final TokenService tokenService;

    public EmployeeAuthController(EmployeeService employeeService, TokenService tokenService) {
        this.employeeService = employeeService;
        this.tokenService = tokenService;
    }

    /** 登录只返回令牌,不返回员工资料;资料由 {@code GET /auth/me} 提供。 */
    @PostMapping("/login")
    public TokenPairResponse login(@Valid @RequestBody EmployeeLoginRequest request) {
        EmployeeService.LoginResult result = employeeService.login(request.username(), request.password());
        return TokenPairResponse.from(result.tokens());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshTokenRequest request) {
        tokenService.revoke(request.refreshToken());
    }

    @PostMapping("/refresh")
    public TokenPairResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return TokenPairResponse.from(tokenService.rotate(request.refreshToken()));
    }

    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        employeeService.changePassword(
                CurrentPrincipal.require().id(), request.oldPassword(), request.newPassword());
    }

    @GetMapping("/me")
    public EmployeeResponse me() {
        return EmployeeResponse.from(employeeService.requireById(CurrentPrincipal.require().id()));
    }
}
