package com.sky.identity.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.sky.identity.domain.EmployeeRole;

/** 编辑员工。用户名不可改,所以请求体里没有它。 */
public record EmployeeUpdateRequest(
        @NotBlank(message = "姓名不能为空")
        @Size(max = 32, message = "姓名最长 32 位")
        String name,

        @Pattern(regexp = "^$|^1[3-9][0-9]{9}$", message = "手机号格式不正确")
        String phone,

        @NotNull(message = "角色不能为空")
        EmployeeRole role,

        @NotNull(message = "状态不能为空")
        Integer status
) {
}
