package com.sky.identity.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sky.identity.api.dto.CustomerWechatLoginRequest;
import com.sky.identity.api.dto.RefreshTokenRequest;
import com.sky.identity.service.CustomerService;
import com.sky.security.TokenPairResponse;
import com.sky.security.TokenService;

/** 顾客端认证:微信登录、续期、登出。 */
@RestController
@RequestMapping("/api/v1/customer/auth")
public class CustomerAuthController {

    private final CustomerService customerService;
    private final TokenService tokenService;

    public CustomerAuthController(CustomerService customerService, TokenService tokenService) {
        this.customerService = customerService;
        this.tokenService = tokenService;
    }

    @PostMapping("/wechat-login")
    public TokenPairResponse wechatLogin(@Valid @RequestBody CustomerWechatLoginRequest request) {
        CustomerService.LoginResult result = customerService.loginByWechat(
                request.code(), request.nickname(), request.avatarUrl());
        return TokenPairResponse.from(result.tokens());
    }

    @PostMapping("/refresh")
    public TokenPairResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return TokenPairResponse.from(tokenService.rotate(request.refreshToken()));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshTokenRequest request) {
        tokenService.revoke(request.refreshToken());
    }
}
