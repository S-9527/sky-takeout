package com.sky.storage.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

import com.sky.common.error.BusinessException;
import com.sky.common.error.ErrorResponse;
import com.sky.common.web.TraceIdFilter;
import com.sky.storage.api.dto.UploadResultResponse;
import com.sky.storage.domain.StorageErrorCode;
import com.sky.storage.service.StorageService;

/**
 * 管理端图片上传。前缀由 SecurityConfig 限制为 ADMIN / STAFF。
 *
 * <p>{@code file} 参数刻意声明为 {@code required = false}:字段缺失时契约要的是
 * 400 {@code UPLOAD_EMPTY_FILE},而 Spring 默认会先抛参数缺失异常(变成
 * {@code COMMON_VALIDATION_FAILED})。
 */
@RestController
@RequestMapping("/api/v1/admin/uploads")
public class UploadController {

    private final StorageService storageService;

    public UploadController(StorageService storageService) {
        this.storageService = storageService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UploadResultResponse upload(@RequestParam(value = "file", required = false) MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(StorageErrorCode.UPLOAD_EMPTY_FILE);
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException ex) {
            throw new BusinessException(StorageErrorCode.UPLOAD_STORE_FAILED);
        }
        return UploadResultResponse.from(
                storageService.store(file.getOriginalFilename(), file.getContentType(), content));
    }

    /**
     * 超过容器上限的请求在进入方法体之前就被 Spring 拒了,所以要在控制器里接住它,
     * 才能给出契约里的 400 {@code UPLOAD_FILE_TOO_LARGE}(而不是 500)。
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleTooLarge(MaxUploadSizeExceededException ex) {
        ErrorResponse body = new ErrorResponse(StorageErrorCode.UPLOAD_FILE_TOO_LARGE.code(),
                StorageErrorCode.UPLOAD_FILE_TOO_LARGE.defaultMessage(), java.util.List.of(),
                TraceIdFilter.currentTraceId());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }
}
