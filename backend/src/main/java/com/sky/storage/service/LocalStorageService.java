package com.sky.storage.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import com.sky.common.error.BusinessException;
import com.sky.common.error.ErrorResponse;
import com.sky.common.util.Times;
import com.sky.storage.domain.StorageErrorCode;

/**
 * 本地落盘实现:文件写到 {@code sky.storage.local-dir} 下按日期分目录,对外 URL 用
 * {@code sky.storage.public-base-url} 拼;两者都由配置决定,代码里没有硬编码路径。
 *
 * <p>校验规则(契约 §2.4 第 7 项):jpg/jpeg/png/webp、单文件 5MB。
 * 扩展名与内容类型**都要**白名单里:只信其中一个都容易被绕过(改扩展名 / 伪造头)。
 */
@Service
@ConditionalOnProperty(name = "sky.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalStorageService implements StorageService {

    /** 契约 §2.4:单文件 5MB。 */
    public static final long MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024;

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final Set<String> ALLOWED_CONTENT_TYPES =
            Set.of("image/jpeg", "image/jpg", "image/png", "image/webp");

    private static final DateTimeFormatter DATE_DIR = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final Path rootDir;
    private final String publicBaseUrl;

    public LocalStorageService(StorageProperties properties) {
        this.rootDir = Paths.get(properties.localDir()).toAbsolutePath().normalize();
        this.publicBaseUrl = trimTrailingSlash(properties.publicBaseUrl());
    }

    @Override
    public StoredFile store(String originalFilename, String contentType, byte[] content) {
        if (content == null || content.length == 0) {
            throw new BusinessException(StorageErrorCode.UPLOAD_EMPTY_FILE);
        }
        if (content.length > MAX_FILE_SIZE_BYTES) {
            throw new BusinessException(StorageErrorCode.UPLOAD_FILE_TOO_LARGE,
                    "图片不能超过 5MB",
                    java.util.List.of(new ErrorResponse.Detail("file",
                            "当前 " + content.length + " 字节,上限 " + MAX_FILE_SIZE_BYTES + " 字节")));
        }
        String extension = extensionOf(originalFilename);
        if (!ALLOWED_EXTENSIONS.contains(extension) || !isAllowedContentType(contentType)) {
            throw new BusinessException(StorageErrorCode.UPLOAD_TYPE_NOT_ALLOWED,
                    "仅支持 jpg/png/webp 格式",
                    java.util.List.of(new ErrorResponse.Detail("file",
                            "扩展名=" + (extension.isEmpty() ? "(空)" : extension)
                                    + ",contentType=" + (contentType == null ? "(空)" : contentType))));
        }

        String relativePath = Times.nowLocal().format(DATE_DIR) + "/"
                + UUID.randomUUID().toString().replace("-", "") + "." + extension;
        Path target = rootDir.resolve(relativePath).normalize();
        if (!target.startsWith(rootDir)) {
            // 正常路径构造不出这种值;真出现了说明有人在文件名上做文章
            throw new BusinessException(StorageErrorCode.UPLOAD_TYPE_NOT_ALLOWED);
        }
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException ex) {
            throw new BusinessException(StorageErrorCode.UPLOAD_STORE_FAILED);
        }
        return new StoredFile(publicBaseUrl + "/" + relativePath, content.length,
                contentType == null ? null : contentType.toLowerCase(Locale.ROOT));
    }

    /** 供 Web 层注册静态资源路由用。 */
    public Path rootDir() {
        return rootDir;
    }

    private static boolean isAllowedContentType(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return false;
        }
        return ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT));
    }

    private static String extensionOf(String filename) {
        if (!StringUtils.hasText(filename)) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String trimTrailingSlash(String url) {
        return url != null && url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
