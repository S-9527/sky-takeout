package com.sky.identity.api.dto;

import jakarta.validation.constraints.NotBlank;

/** 刷新与登出共用同一请求体。 */
public record RefreshTokenRequest(
        @NotBlank(message = "refreshToken 不能为空")
        String refreshToken
) {
}
