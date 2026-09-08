package com.sky.storage;

import com.sky.utils.AliOssUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 阿里云 OSS 存储实现：默认，delegate 到 AliOssUtil。
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "sky.oss.storage", havingValue = "aliyun", matchIfMissing = true)
public class AliOssStorage implements StorageService {

    private final AliOssUtil aliOssUtil;

    @Override
    public String upload(byte[] bytes, String objectName) {
        return aliOssUtil.upload(bytes, objectName);
    }
}