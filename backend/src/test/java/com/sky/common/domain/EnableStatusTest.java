package com.sky.common.domain;

import org.junit.jupiter.api.Test;

import com.sky.common.error.BusinessException;
import com.sky.common.error.CommonErrorCode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnableStatusTest {

    @Test
    void mapsDatabaseTinyintBothWays() {
        assertThat(EnableStatus.of(1)).isEqualTo(EnableStatus.ENABLED);
        assertThat(EnableStatus.of(0)).isEqualTo(EnableStatus.DISABLED);
        assertThat(EnableStatus.ENABLED.getValue()).isEqualTo(1);
        assertThat(EnableStatus.DISABLED.getValue()).isZero();
    }

    @Test
    void isEnabledReadsNaturally() {
        assertThat(EnableStatus.ENABLED.isEnabled()).isTrue();
        assertThat(EnableStatus.DISABLED.isEnabled()).isFalse();
    }

    /** null 与越界值都报 400:静默当成"禁用"会让前端的问题永远暴露不出来。 */
    @Test
    void nullAndUnknownValuesAreRejected() {
        assertThatThrownBy(() -> EnableStatus.of(null))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(CommonErrorCode.COMMON_VALIDATION_FAILED));

        assertThatThrownBy(() -> EnableStatus.of(2))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(CommonErrorCode.COMMON_VALIDATION_FAILED));
    }
}
