package com.sky.cart.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FlavorKeysTest {

    /** 未选口味必须是固定空串(与列默认值一致),不能是 hash("")。 */
    @Test
    void emptyChoiceMapsToEmptyKey() {
        assertThat(FlavorKeys.of(null)).isEmpty();
        assertThat(FlavorKeys.of(List.of())).isEmpty();
    }

    @Test
    void sameChoicesInDifferentOrderProduceTheSameKey() {
        String first = FlavorKeys.of(List.of(new FlavorChoice("辣度", "微辣"), new FlavorChoice("忌口", "不要葱")));
        String reversed = FlavorKeys.of(List.of(new FlavorChoice("忌口", "不要葱"), new FlavorChoice("辣度", "微辣")));

        assertThat(first).isEqualTo(reversed);
    }

    @Test
    void differentChoicesProduceDifferentKeys() {
        String one = FlavorKeys.of(List.of(new FlavorChoice("辣度", "微辣")));
        String two = FlavorKeys.of(List.of(new FlavorChoice("辣度", "中辣")));
        String other = FlavorKeys.of(List.of(new FlavorChoice("忌口", "微辣")));

        assertThat(one).isNotEqualTo(two).isNotEqualTo(other);
    }

    /** 长度前缀编码:不能让 (a, b=c) 与 (a=b, c) 撞成同一个 key。 */
    @Test
    void encodingIsNotAmbiguousAcrossSeparators() {
        String first = FlavorKeys.of(List.of(new FlavorChoice("a", "b=c")));
        String second = FlavorKeys.of(List.of(new FlavorChoice("a=b", "c")));

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void keyFitsTheColumnWidth() {
        assertThat(FlavorKeys.of(List.of(new FlavorChoice("辣度", "微辣"))))
                .hasSize(64)
                .matches("[0-9a-f]{64}");
    }
}
