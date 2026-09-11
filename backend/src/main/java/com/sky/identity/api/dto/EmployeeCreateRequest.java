package com.sky.identity.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.sky.identity.domain.EmployeeRole;

public record EmployeeCreateRequest(
        @NotBlank(message = "用户名不能为空")
        @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9_]{2,31}$",
                message = "用户名需以字母开头,由字母、数字或下划线组成,长度 3~32 位")
        String username,

        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 64, message = "密码长度需在 6~64 位之间")
        String password,

        @NotBlank(message = "姓名不能为空")
        @Size(max = 32, message = "姓名最长 32 位")
        String name,

        @Pattern(regexp = "^$|^1[3-9][0-9]{9}$", message = "手机号格式不正确")
        String phone,

        @NotNull(message = "角色不能为空")
        EmployeeRole role
) {
}
