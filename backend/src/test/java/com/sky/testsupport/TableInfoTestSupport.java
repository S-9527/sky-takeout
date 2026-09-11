package com.sky.testsupport;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;

/**
 * MyBatis-Plus 单测支持。
 *
 * <p>没有 Spring 容器时,{@code TableInfo} 不会自动初始化,而 {@code Wrappers.lambdaUpdate().in(Entity::getId, ...)}
 * 这类调用会**立刻**去查 lambda 缓存,于是报
 * {@code MybatisPlus can not find lambda cache for this entity}。测试里显式注册一次即可。
 */
public final class TableInfoTestSupport {

    private TableInfoTestSupport() {
    }

    public static void register(Class<?>... entityTypes) {
        for (Class<?> entityType : entityTypes) {
            MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "test");
            assistant.setCurrentNamespace("test");
            TableInfoHelper.initTableInfo(assistant, entityType);
        }
    }
}
