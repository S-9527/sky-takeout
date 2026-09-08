package com.sky.storage;

/**
 * 文件存储策略接口：屏蔽阿里云 OSS 与本地文件存储的差异。
 */
public interface StorageService {

    /**
     * 文件上传
     *
     * @param bytes      文件字节
     * @param objectName 存储对象名
     * @return 文件访问 URL
     */
    String upload(byte[] bytes, String objectName);
}