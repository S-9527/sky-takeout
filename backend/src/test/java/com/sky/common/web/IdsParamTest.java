package com.sky.common.web;

import org.junit.jupiter.api.Test;

import com.sky.common.error.BusinessException;
import com.sky.common.error.CommonErrorCode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdsParamTest {

    @Test
    void parsesCommaSeparatedIds() {
        assertThat(IdsParam.parse("1,2,3")).containsExactly(1L, 2L, 3L);
        assertThat(IdsParam.parse("42")).containsExactly(42L);
    }

    @Test
    void removesDuplicatesButKeepsOrder() {
        assertThat(IdsParam.parse("3,1,3,2")).containsExactly(3L, 1L, 2L);
    }

    /** 负数、空格、空串、尾随逗号都不符合 openapi 的 pattern,必须 400。 */
    @Test
    void rejectsAnythingOutsideTheContractPattern() {
        for (String invalid : new String[]{"", " ", "1,", ",1", "1,,2", "-1", "1, -2", "a", "1;2", "1.0"}) {
            assertThatThrownBy(() -> IdsParam.parse(invalid))
                    .as("ids=%s", invalid)
                    .isInstanceOfSatisfying(BusinessException.class,
                            ex -> assertThat(ex.errorCode()).isEqualTo(CommonErrorCode.COMMON_VALIDATION_FAILED));
        }
    }

    @Test
    void rejectsNull() {
        assertThatThrownBy(() -> IdsParam.parse(null))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(CommonErrorCode.COMMON_VALIDATION_FAILED));
    }

    /** 正则挡不住超长数字,Long 溢出也必须回 400 而不是 500。 */
    @Test
    void rejectsValuesBeyondLongRange() {
        assertThatThrownBy(() -> IdsParam.parse("99999999999999999999"))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.errorCode()).isEqualTo(CommonErrorCode.COMMON_VALIDATION_FAILED);
                    assertThat(ex.details()).isNotEmpty();
                });
    }
}
