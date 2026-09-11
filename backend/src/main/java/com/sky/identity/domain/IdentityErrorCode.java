package com.sky.identity.domain;

import org.springframework.http.HttpStatus;

import com.sky.common.error.ErrorCode;

/** 身份上下文错误码。取值与 {@code docs/03-api.md} 的 EMPLOYEE_* / CUSTOMER_* 表逐字一致。 */
public enum IdentityErrorCode implements ErrorCode {

    EMPLOYEE_NOT_FOUND(HttpStatus.NOT_FOUND, "员工不存在"),
    EMPLOYEE_USERNAME_TAKEN(HttpStatus.CONFLICT, "该用户名已被使用"),
    EMPLOYEE_DISABLED(HttpStatus.FORBIDDEN, "账号已被禁用,请联系管理员"),
    EMPLOYEE_SELF_DISABLE(HttpStatus.UNPROCESSABLE_ENTITY, "不能禁用当前登录账号"),
    EMPLOYEE_SELF_ROLE_CHANGE(HttpStatus.UNPROCESSABLE_ENTITY, "不能修改自己的角色"),

    CUSTOMER_NOT_FOUND(HttpStatus.NOT_FOUND, "用户不存在"),
    CUSTOMER_DISABLED(HttpStatus.FORBIDDEN, "账号已被封禁,请联系客服"),
    CUSTOMER_WECHAT_CODE_INVALID(HttpStatus.UNPROCESSABLE_ENTITY, "微信登录凭证已失效,请重试"),
    CUSTOMER_WECHAT_API_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "微信登录暂时不可用,请稍后重试"),
    CUSTOMER_DUPLICATE_OPENID(HttpStatus.CONFLICT, "登录中,请重试");

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    IdentityErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    @Override
    public String code() {
        return name();
    }

    @Override
    public HttpStatus httpStatus() {
        return httpStatus;
    }

    @Override
    public String defaultMessage() {
        return defaultMessage;
    }
}
