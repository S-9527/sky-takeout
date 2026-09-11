package com.sky.common.error;

import org.springframework.http.HttpStatus;

/**
 * 与具体业务无关的通用错误码。取值与 {@code docs/03-api.md} 的通用错误码表逐字一致。
 */
public enum CommonErrorCode implements ErrorCode {

    COMMON_VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "请求参数有误,请检查后重试"),
    COMMON_SORT_FIELD_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "不支持按该字段排序"),
    COMMON_METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "请求方法不被支持"),
    COMMON_RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "请求的资源不存在"),
    COMMON_CONCURRENT_MODIFIED(HttpStatus.CONFLICT, "数据已被他人修改,请刷新后重试"),
    COMMON_INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "服务器开小差了,请稍后重试");

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    CommonErrorCode(HttpStatus httpStatus, String defaultMessage) {
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
