package com.sky.security;

import org.springframework.http.HttpStatus;

import com.sky.common.error.ErrorCode;

/**
 * 认证与授权错误码。放在安全包内,避免安全设施反向依赖任何业务上下文。
 *
 * <p>取值与 {@code docs/03-api.md} 的 AUTH_* 表逐字一致。
 */
public enum AuthErrorCode implements ErrorCode {

    /** Authorization 头缺失、格式错误、签名无效——都在这一类里。 */
    AUTH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "登录状态无效,请重新登录"),
    AUTH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "登录状态已过期,请重新登录"),
    AUTH_REFRESH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "登录状态已失效,请重新登录"),
    AUTH_REFRESH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "登录已过期,请重新登录"),
    AUTH_BAD_CREDENTIALS(HttpStatus.UNAUTHORIZED, "用户名或密码错误"),

    /** 令牌 {@code aud} 与接口受众不符:顾客令牌打了管理端接口,或反之。 */
    AUTH_AUDIENCE_MISMATCH(HttpStatus.FORBIDDEN, "当前登录身份无权访问该功能"),
    AUTH_PERMISSION_DENIED(HttpStatus.FORBIDDEN, "没有该操作权限"),

    /** 令牌签名有效,但 {@code sub} 指向的主体已不存在。 */
    AUTH_SUBJECT_NOT_FOUND(HttpStatus.UNAUTHORIZED, "账号不存在,请重新登录");

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    AuthErrorCode(HttpStatus httpStatus, String defaultMessage) {
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
