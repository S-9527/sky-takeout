package com.sky.framework.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 提供 RestClient.Builder 供各模块构造外部 HTTP 调用(RestClient 在 Spring Boot 4 下不再自动配置)
 */
@Configuration
public class HttpClientConfig {

    private static final int TIMEOUT_MSEC = 5 * 1000;

    @Bean
    public RestClient.Builder restClientBuilder() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(TIMEOUT_MSEC);
        requestFactory.setReadTimeout(TIMEOUT_MSEC);
        return RestClient.builder().requestFactory(requestFactory);
    }
}