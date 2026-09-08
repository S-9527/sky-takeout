package com.sky.properties;

import lombok.Data;

/**
 * JWT 单代密钥配置：kid 标识密钥版本，secret 为签名密钥。
 *
 * <p>支持轮换：将新密钥置于列表首位(kid 更新)，历史密钥保留在列表中可继续验签，
 * 直到对所有旧令牌过期后从配置移除。</p>
 */
@Data
public class JwtKeyConfig {

    /**
     * 密钥标识(写入 JWT header 的 kid，用于验签时选择对应密钥)
     */
    private String kid;

    /**
     * HS256 签名密钥(长度不低于 32 字节)
     */
    private String secret;
}