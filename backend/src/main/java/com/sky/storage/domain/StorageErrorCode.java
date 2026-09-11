package com.sky.storage.domain;

import org.springframework.http.HttpStatus;

import com.sky.common.error.ErrorCode;

/** 文件存储错误码。取值与 {@code docs/03-api.md} 的 UPLOAD_* 表逐字一致。 */
public enum StorageErrorCode implements ErrorCode {

    UPLOAD_EMPTY_FILE(HttpStatus.BAD_REQUEST, "请选择要上传的图片"),
    UPLOAD_FILE_TOO_LARGE(HttpStatus.BAD_REQUEST, "图片不能超过 5MB"),
    UPLOAD_TYPE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "仅支持 jpg/png/webp 格式"),
    UPLOAD_STORE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "图片上传失败,请稍后重试");

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    StorageErrorCode(HttpStatus httpStatus, String defaultMessage) {
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
