package com.sky.identity.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

import com.sky.common.domain.EnableStatus;
import com.sky.common.persistence.AuditableEntity;

/** 员工(管理端账号)。 */
@Getter
@Setter
@TableName("employee")
public class Employee extends AuditableEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    /**
     * BCrypt 哈希。任何时候都不得出现在响应体里。
     *
     * <p>{@code @JsonIgnore} 是防御性的:实体本不该被直接序列化出去,但万一有人漏了 DTO 映射,
     * 这一步能兜住,不至于把口令哈希发到前端。
     */
    @JsonIgnore
    private String passwordHash;

    private String name;

    private String phone;

    private EmployeeRole role;

    private EnableStatus status;

    private LocalDateTime lastLoginAt;

    public boolean isEnabled() {
        return status != null && status.isEnabled();
    }
}
