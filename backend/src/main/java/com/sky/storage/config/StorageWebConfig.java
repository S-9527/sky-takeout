package com.sky.storage.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

import com.sky.storage.service.StorageProperties;

/**
 * 把本地落盘目录挂到 {@code /files/**}(与 {@code sky.storage.public-base-url} 一致)。
 *
 * <p>该路径在 SecurityConfig 里是放行的:种子数据与上传结果里的图片要能被 &lt;img&gt; 直接引用。
 */
@Configuration
@ConditionalOnProperty(name = "sky.storage.type", havingValue = "local", matchIfMissing = true)
public class StorageWebConfig implements WebMvcConfigurer {

    private final StorageProperties properties;

    public StorageWebConfig(StorageProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 用 toUri() 而不是手拼 "file:" + path:能正确处理空格与 Windows 盘符
        String location = Paths.get(properties.localDir()).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler("/files/**").addResourceLocations(location);
    }
}
