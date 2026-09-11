package com.sky.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Web 层配置,对应 {@code sky.web.*}。
 *
 * @param corsAllowedOriginPatterns 允许跨域的来源模式。开发默认放开本机任意端口,
 *                                  生产必须收紧为具体域名。
 */
@ConfigurationProperties(prefix = "sky.web")
public record WebProperties(List<String> corsAllowedOriginPatterns) {
}
