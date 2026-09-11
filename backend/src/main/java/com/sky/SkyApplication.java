package com.sky;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 苍穹外卖 v2 启动类。
 *
 * <p>后端是模块化单体:按限界上下文分包,包内再分层(api / service / domain / mapper / gateway),
 * 上下文之间只允许通过对方的 service 调用,具体规则由架构测试强制。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
// 定时任务:目前只有「未支付订单超时关单」(领域 §4 的 15 分钟)
@EnableScheduling
public class SkyApplication {

    public static void main(String[] args) {
        SpringApplication.run(SkyApplication.class, args);
    }
}
