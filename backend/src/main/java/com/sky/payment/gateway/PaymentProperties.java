package com.sky.payment.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 支付配置,对应 {@code sky.payment.*}。
 *
 * @param gateway {@code mock}(默认,发起即成功)或 {@code wechat}(真实微信支付,尚未实现)
 */
@ConfigurationProperties(prefix = "sky.payment")
public record PaymentProperties(String gateway) {
}
