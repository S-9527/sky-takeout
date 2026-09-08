package com.sky.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 本地文件存储实现：开发环境无需阿里云账号即可上传并访问文件。
 * 文件访问 URL 形如 /files/{objectName}，由 WebMvcConfiguration 中的静态资源映射提供。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "sky.oss.storage", havingValue = "local")
public class LocalStorage implements StorageService {

    @Value("${sky.oss.local-dir:./data/upload}")
    private String localDir;

    @Override
    public String upload(byte[] bytes, String objectName) {
        try {
            Path dir = Paths.get(localDir).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            Path target = dir.resolve(objectName).normalize();
            if (!target.startsWith(dir)) {
                throw new IllegalArgumentException("非法文件名：" + objectName);
            }
            Files.write(target, bytes);
            log.debug("文件上传到本地:{}", target);
            return "/files/" + objectName;
        } catch (IOException e) {
            log.error("文件上传失败：{}", e.getMessage(), e);
            throw new RuntimeException("文件上传失败", e);
        }
    }
}