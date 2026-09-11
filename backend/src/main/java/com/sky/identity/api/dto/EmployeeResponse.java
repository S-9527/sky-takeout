package com.sky.identity.api.dto;

import java.time.OffsetDateTime;

import com.sky.common.util.Times;
import com.sky.identity.domain.Employee;

/** 对外的员工资料。刻意没有 passwordHash 字段——不是"忘了加",而是类型上就不允许泄漏。 */
public record EmployeeResponse(
        Long id,
        String username,
        String name,
        String phone,
        String role,
        Integer status,
        OffsetDateTime lastLoginAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        Long createdBy,
        Long updatedBy
) {

    public static EmployeeResponse from(Employee employee) {
        return new EmployeeResponse(
                employee.getId(),
                employee.getUsername(),
                employee.getName(),
                employee.getPhone(),
                employee.getRole() == null ? null : employee.getRole().name(),
                employee.getStatus() == null ? null : employee.getStatus().getValue(),
                Times.toOffset(employee.getLastLoginAt()),
                Times.toOffset(employee.getCreatedAt()),
                Times.toOffset(employee.getUpdatedAt()),
                employee.getCreatedBy(),
                employee.getUpdatedBy());
    }
}
