package com.sky.identity.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "原密码不能为空")
        @Size(min = 6, max = 64, message = "密码长度需在 6~64 位之间")
        String oldPassword,

        @NotBlank(message = "新密码不能为空")
        @Size(min = 6, max = 64, message = "密码长度需在 6~64 位之间")
        String newPassword
) {
}
