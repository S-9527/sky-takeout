package com.sky.profile.domain;

import org.springframework.http.HttpStatus;

import com.sky.common.error.ErrorCode;

/** 顾客资料(地址簿)上下文错误码。取值与 {@code docs/03-api.md} 的 ADDRESS_* 表逐字一致。 */
public enum ProfileErrorCode implements ErrorCode {

    /** 不存在、或不属于当前顾客——刻意不区分,避免变成"这个 id 存不存在"的探测器(R9)。 */
    ADDRESS_NOT_FOUND(HttpStatus.NOT_FOUND, "地址不存在"),

    ADDRESS_LIMIT_EXCEEDED(HttpStatus.UNPROCESSABLE_ENTITY, "地址数量已达上限,请先删除"),

    /**
     * 并发把两条地址同时置为默认时,事务里的加锁计数会发现"默认不止一条"。
     * 单条 UPDATE 拦不住这种情况(库层没有 "每顾客至多一条 is_default=1" 的约束)。
     */
    ADDRESS_DEFAULT_DUPLICATED(HttpStatus.CONFLICT, "操作冲突,请刷新后重试");

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ProfileErrorCode(HttpStatus httpStatus, String defaultMessage) {
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
