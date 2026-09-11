package com.sky.storage.service;

/**
 * 文件存储端口。本地开发落盘并经由 {@code /files/**} 暴露;生产可替换为对象存储实现
 * (后端架构 §4.7)。业务代码只依赖这个接口,不关心文件存在哪。
 */
public interface StorageService {

    /**
     * 保存一个文件。
     *
     * @param originalFilename 原始文件名(用来取扩展名)
     * @param contentType      声明的 MIME 类型
     * @param content          文件字节
     * @return 存储结果(含可公开访问的 URL)
     */
    StoredFile store(String originalFilename, String contentType, byte[] content);
}
