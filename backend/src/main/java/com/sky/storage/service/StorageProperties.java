package com.sky.storage.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 存储配置,对应 {@code sky.storage.*}。
 *
 * @param type          实现选择:{@code local}(默认)落盘
 * @param localDir      本地落盘目录
 * @param publicBaseUrl 对外访问前缀(与 {@code /files/**} 路由一致)
 */
@ConfigurationProperties(prefix = "sky.storage")
public record StorageProperties(String type, String localDir, String publicBaseUrl) {

    public StorageProperties {
        type = type == null ? "local" : type;
        localDir = localDir == null ? "./data/upload" : localDir;
        publicBaseUrl = publicBaseUrl == null ? "http://localhost:8080/files" : publicBaseUrl;
    }
}
