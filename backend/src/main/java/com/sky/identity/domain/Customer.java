package com.sky.identity.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

import com.sky.common.domain.EnableStatus;
import com.sky.common.persistence.AuditableEntity;

/**
 * 顾客(小程序用户)。
 *
 * <p>叫 Customer 而不是 User:旧代码里 {@code user} 同时指员工账号和顾客,是这个系统长期歧义的来源。
 */
@Getter
@Setter
@TableName("customer")
public class Customer extends AuditableEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 微信 openid,唯一。顾客没有密码,这是唯一的身份来源。 */
    private String openid;

    private String nickname;

    private String avatarUrl;

    private String phone;

    private EnableStatus status;

    private LocalDateTime lastLoginAt;

    public boolean isEnabled() {
        return status != null && status.isEnabled();
    }
}
