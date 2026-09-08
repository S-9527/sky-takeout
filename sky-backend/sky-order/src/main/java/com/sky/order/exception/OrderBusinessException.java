package com.sky.order.exception;

import com.sky.exception.BaseException;

public class OrderBusinessException extends BaseException {

    public OrderBusinessException(int code, String msg) {
        super(code, msg);
    }

    public OrderBusinessException(String msg) {
        super(msg);
    }

}
