package com.sky.common.error;

import org.springframework.http.HttpStatus;

/**
 * 业务错误码。
 *
 * <p>实现类的 {@code name()} 即为对外暴露的错误码字符串,取值必须与 {@code docs/03-api.md} 的错误码表逐字一致。
 * 枚举常量命名形如 {@code ORDER_INVALID_TRANSITION},前缀表示所属上下文。
 */
public interface ErrorCode {

    /** 对外错误码,SCREAMING_SNAKE_CASE。 */
    String code();

    /** 该错误对应的 HTTP 状态码。 */
    HttpStatus httpStatus();

    /** 面向用户的中文默认提示。 */
    String defaultMessage();
}
