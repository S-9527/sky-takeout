package com.sky.security;

/**
 * 令牌受众。顾客令牌与管理端令牌**在签名载荷里就区分开**,不能只靠路径判断——
 * 否则一个顾客令牌就能直接打管理端接口。
 */
public enum Audience {

    /** 管理端员工。 */
    ADMIN,

    /** 小程序顾客。 */
    CUSTOMER;
}
