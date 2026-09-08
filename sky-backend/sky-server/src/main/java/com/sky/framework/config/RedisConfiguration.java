package com.sky.framework.config;

import com.sky.menu.vo.DishVO;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.util.List;

@Configuration
public class RedisConfiguration {

    /**
     * 通用 Redis 模板：key 为字符串，value 为任意对象(JDK 序列化)
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory redisConnectionFactory) {
        return buildRedisTemplate(redisConnectionFactory);
    }

    /**
     * 店铺营业状态专用模板：value 为 Integer
     */
    @Bean
    public RedisTemplate<String, Integer> shopStatusRedisTemplate(RedisConnectionFactory redisConnectionFactory) {
        return buildRedisTemplate(redisConnectionFactory);
    }

    /**
     * 菜品缓存专用模板：value 为 List&lt;DishVO&gt;
     */
    @Bean
    public RedisTemplate<String, List<DishVO>> dishCacheRedisTemplate(RedisConnectionFactory redisConnectionFactory) {
        return buildRedisTemplate(redisConnectionFactory);
    }

    private <T> RedisTemplate<String, T> buildRedisTemplate(RedisConnectionFactory redisConnectionFactory) {
        RedisTemplate<String, T> redisTemplate = new RedisTemplate<>();
        //设置redis的连接工厂对象
        redisTemplate.setConnectionFactory(redisConnectionFactory);
        //设置redis key的序列化器
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        return redisTemplate;
    }
}
