package com.sky.menu.exception;

import com.sky.exception.BaseException;

public class DeletionNotAllowedException extends BaseException {

    public DeletionNotAllowedException(String msg) {
        super(msg);
    }

}
