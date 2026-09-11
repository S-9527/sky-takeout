package com.sky.identity.api.dto;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import com.sky.common.domain.EnableStatus;
import com.sky.identity.domain.Customer;
import com.sky.identity.domain.Employee;
import com.sky.identity.domain.EmployeeRole;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityDtoMappingTest {

    private static Employee employee() {
        Employee employee = new Employee();
        employee.setId(1L);
        employee.setUsername("admin");
        employee.setPasswordHash("$2a$10$should-never-be-serialised");
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

    @Test
    void employeeResponseMapsEveryFieldAndConvertsTimes() {
        EmployeeResponse response = EmployeeResponse.from(employee());

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.username()).isEqualTo("admin");
        assertThat(response.name()).isEqualTo("管理员");
        assertThat(response.phone()).isEqualTo("13800000001");
        assertThat(response.role()).isEqualTo("ADMIN");
        assertThat(response.status()).isEqualTo(1);
        assertThat(response.lastLoginAt().toString()).isEqualTo("2026-01-02T03:04:05+08:00");
        assertThat(response.createdAt().toString()).isEqualTo("2026-01-01T00:00+08:00");
        assertThat(response.updatedBy()).isEqualTo(1L);
    }

    /** 类型上就没有 passwordHash 字段:不是"忘了过滤",而是根本放不进去。 */
    @Test
    void employeeResponseCannotCarryPasswordHash() {
        EmployeeResponse response = EmployeeResponse.from(employee());

        assertThat(EmployeeResponse.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .doesNotContain("passwordHash");
        assertThat(response.toString()).doesNotContain("should-never-be-serialised");
    }

    @Test
    void employeeResponseToleratesUnsetFields() {
        EmployeeResponse response = EmployeeResponse.from(new Employee());

        assertThat(response.role()).isNull();
        assertThat(response.status()).isNull();
        assertThat(response.lastLoginAt()).isNull();
        assertThat(response.createdAt()).isNull();
    }

    @Test
    void customerResponseMapsEveryFieldButNeverOpenid() {
        Customer customer = new Customer();
        customer.setId(9L);
        customer.setOpenid("openid-9");
        customer.setNickname("小明");
        customer.setAvatarUrl("http://avatar/1.png");
        customer.setPhone("13900000000");
        customer.setStatus(EnableStatus.DISABLED);
        customer.setLastLoginAt(LocalDateTime.of(2026, 1, 2, 3, 4, 5));

        CustomerResponse response = CustomerResponse.from(customer);

        assertThat(response.id()).isEqualTo(9L);
        assertThat(response.nickname()).isEqualTo("小明");
        assertThat(response.avatarUrl()).isEqualTo("http://avatar/1.png");
        assertThat(response.phone()).isEqualTo("13900000000");
        assertThat(response.status()).isZero();
        assertThat(CustomerResponse.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .doesNotContain("openid");
    }

    @Test
    void customerResponseToleratesUnsetStatus() {
        assertThat(CustomerResponse.from(new Customer()).status()).isNull();
    }
}
