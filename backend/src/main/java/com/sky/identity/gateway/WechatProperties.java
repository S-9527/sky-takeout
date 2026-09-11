package com.sky.identity.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 微信配置,对应 {@code sky.wechat.*}。
 *
 * @param appId     小程序 appId
 * @param appSecret 小程序密钥
 * @param mock      为 true 时使用 {@link MockWechatAuthClient},不调微信接口
 */
@ConfigurationProperties(prefix = "sky.wechat")
public record WechatProperties(String appId, String appSecret, boolean mock) {
}
