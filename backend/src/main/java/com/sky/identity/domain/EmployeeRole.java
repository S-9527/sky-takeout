package com.sky.identity.domain;

import com.baomidou.mybatisplus.annotation.IEnum;

/**
 * 员工角色。数据库存 VARCHAR:ADMIN / STAFF。
 *
 * <p>刻意做成显式字段而不是"用户名等于 admin 就是超管":角色要能被授予、被审计、被写进令牌。
 */
public enum EmployeeRole implements IEnum<String> {

    /** 超管:拥有全部权限,含员工管理与店铺设置。 */
    ADMIN,

    /** 普通员工:日常经营(订单、商品、报表),不能管理员工。 */
    STAFF;

    @Override
    public String getValue() {
        return name();
    }

    public boolean isAdmin() {
        return this == ADMIN;
    }
}
