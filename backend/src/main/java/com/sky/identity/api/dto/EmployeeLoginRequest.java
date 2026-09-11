package com.sky.identity.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EmployeeLoginRequest(
        @NotBlank(message = "用户名不能为空")
        @Size(max = 32, message = "用户名最长 32 位")
        String username,

        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 64, message = "密码长度需在 6~64 位之间")
        String password
) {
}
