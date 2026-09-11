package com.sky.common.util;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JsonTest {

    /** D7 的核心:选项文本里带逗号也不会损坏——旧实现正是死在这里。 */
    @Test
    void roundTripsStringsContainingCommas() {
        List<String> options = List.of("微辣,少油", "不要葱,不要蒜", "正常");

        String json = Json.write(options);

        assertThat(json).startsWith("[");
        assertThat(Json.readStringList(json)).isEqualTo(options);
    }

    @Test
    void roundTripsEmptyList() {
        assertThat(Json.readStringList(Json.write(List.of()))).isEmpty();
    }

    @Test
    void nullOrBlankBecomesEmptyList() {
        assertThat(Json.readStringList(null)).isEmpty();
        assertThat(Json.readStringList("   ")).isEmpty();
    }

    /** 库里万一有 null 元素,读的时候不应该抛异常(写路径有 DTO 校验挡住 null)。 */
    @Test
    void toleratesNullElementsOnRead() {
        List<String> values = Json.readStringList("[\"a\",null]");

        assertThat(values).hasSize(2);
        assertThat(values.get(0)).isEqualTo("a");
        assertThat(values.get(1)).isNull();
    }

    @Test
    void writesPlainObjectsToo() {
        assertThat(Json.write(java.util.Map.of("k", "v"))).isEqualTo("{\"k\":\"v\"}");
        assertThat(Json.write(Arrays.asList(1, 2))).isEqualTo("[1,2]");
    }

    /** 对象列表往返:购物车的 flavor_choice 就是这种形状。 */
    @Test
    void roundTripsObjectLists() {
        List<FlavorChoiceSample> choices = List.of(
                new FlavorChoiceSample("辣度", "微辣"), new FlavorChoiceSample("忌口", "不要葱,不要蒜"));

        List<FlavorChoiceSample> parsed = Json.readList(Json.write(choices), FlavorChoiceSample.class);

        assertThat(parsed).isEqualTo(choices);
    }

    @Test
    void readListToleratesNullAndBlank() {
        assertThat(Json.readList(null, FlavorChoiceSample.class)).isEmpty();
        assertThat(Json.readList("  ", FlavorChoiceSample.class)).isEmpty();
    }

    private record FlavorChoiceSample(String name, String option) {
    }
}
