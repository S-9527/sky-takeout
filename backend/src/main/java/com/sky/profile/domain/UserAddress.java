package com.sky.profile.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import com.sky.common.persistence.AuditableEntity;

/**
 * 顾客收货地址。
 *
 * <p>"每顾客至多一条默认地址"由**应用层**保证:库里只有 {@code idx_user_address_customer} 索引,
 * 没有 "is_default=1 唯一" 的约束(部分索引 MySQL 不支持)。所以设置默认的写操作都在同一事务里
 * "先清空其它、再置默认",并用加锁计数兜住并发。
 */
@Getter
@Setter
@TableName("user_address")
public class UserAddress extends AuditableEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long customerId;

    private String consignee;

    private String phone;

    private String province;

    private String city;

    private String district;

    private String detail;

    /** 约定取值 家 / 公司 / 学校,允许自定义,可空。 */
    private String label;

    /** true 表示默认地址。数据库是 TINYINT 1/0。 */
    private Boolean isDefault;
}
