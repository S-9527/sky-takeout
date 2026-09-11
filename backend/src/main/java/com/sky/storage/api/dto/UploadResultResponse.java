package com.sky.storage.api.dto;

import com.sky.storage.service.StoredFile;

/** 上传结果。对应 openapi 的 {@code UploadResult}。 */
public record UploadResultResponse(String url, long size, String contentType) {

    public static UploadResultResponse from(StoredFile file) {
        return new UploadResultResponse(file.url(), file.size(), file.contentType());
    }
}
