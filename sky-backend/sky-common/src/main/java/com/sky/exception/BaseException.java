package com.sky.exception;

import com.sky.result.ResultCode;
import lombok.Getter;

/**
 * 业务异常
 */
@Getter
public class BaseException extends RuntimeException {

    private final Integer code;

    public BaseException() {
        this(ResultCode.ERROR.getCode(), null);
    }

    public BaseException(String msg) {
        this(ResultCode.ERROR.getCode(), msg);
    }

    public BaseException(int code, String msg) {
        super(msg);
        this.code = code;
    }

}