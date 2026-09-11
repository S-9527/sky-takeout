package com.sky.common.util;

import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.List;

/**
 * JSON 转换的唯一入口。
 *
 * <p>和 {@link Times} 一样,把"用哪个 JSON 实现"收在一个地方:Boot 4 默认 Jackson 3
 * ({@code tools.jackson}),而 MyBatis-Plus 自带的 JSON TypeHandler 还编译在 Jackson 2 上。
 * 如果各处混用,项目里就会同时存在两套 JSON 配置与两套异常体系。
 *
 * <p>实体上的 JSON 列因此存**字符串**字段,由 getter/setter 转换成强类型(见
 * {@code DishFlavor#getOptions()}),而不是挂一个 Jackson 2 的 TypeHandler。
 */
public final class Json {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Json() {
    }

    public static String write(Object value) {
        return MAPPER.writeValueAsString(value);
    }

    /** JSON 字符串数组 → List;null / 空白 → 空列表。 */
    public static List<String> readStringList(String json) {
        return readList(json, String.class);
    }

    /**
     * JSON 数组 → 对象列表;null / 空白 → 空列表。
     *
     * <p>用"数组 class"而不是 TypeFactory:Jackson 3 的类型工厂 API 与 2.x 不同,
     * 而 {@code Array.newInstance} 在任何版本上都稳定。
     */
    @SuppressWarnings("unchecked")
    public static <T> List<T> readList(String json, Class<T> elementType) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        Object array = MAPPER.readValue(json, Array.newInstance(elementType, 0).getClass());
        // 用 Arrays.asList 而不是 List.of:数据库里万一有 null 元素,不应该在读的时候炸掉
        return array == null ? List.of() : Arrays.asList((T[]) array);
    }
}
