package com.sky.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "sky.jwt")
@Data
public class JwtProperties {

    /**
     * JWT 受众
     */
    private String audience;

    /**
     * 管理端员工生成jwt令牌相关配置
     */
    private String adminIssuer;
    private long adminTtl;
    private long adminRefreshTtl;
    private List<JwtKeyConfig> adminKeys = new ArrayList<>();

    /**
     * 用户端微信用户生成jwt令牌相关配置
     */
    private String userIssuer;
    private long userTtl;
    private long userRefreshTtl;
    private List<JwtKeyConfig> userKeys = new ArrayList<>();

}