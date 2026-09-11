package com.sky.storage.service;

/** 存储结果:可公开访问的 URL + 字节数 + 实际内容类型。 */
public record StoredFile(String url, long size, String contentType) {
}
