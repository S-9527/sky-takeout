package com.sky.common.domain;

import org.junit.jupiter.api.Test;

import java.util.Set;

import com.sky.common.error.BusinessException;
import com.sky.common.error.CommonErrorCode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SortSpecTest {

    private static final Set<String> WHITELIST = Set.of("createdAt", "name");

    @Test
    void missingSortUsesGivenDefaults() {
        assertThat(SortSpec.parse(null, WHITELIST, "createdAt", true))
                .isEqualTo(new SortSpec("createdAt", true));
        assertThat(SortSpec.parse("  ", WHITELIST, "name", false))
                .isEqualTo(new SortSpec("name", false));
    }

    @Test
    void fieldWithoutDirectionKeepsDefaultDirection() {
        assertThat(SortSpec.parse("name", WHITELIST, "createdAt", true).descending()).isTrue();
        assertThat(SortSpec.parse("name", WHITELIST, "createdAt", false).descending()).isFalse();
    }

    @Test
    void directionIsCaseInsensitiveAndTrimmed() {
        SortSpec ascending = SortSpec.parse(" name , ASC ", WHITELIST, "createdAt", true);
        assertThat(ascending.field()).isEqualTo("name");
        assertThat(ascending.ascending()).isTrue();

        assertThat(SortSpec.parse("name,desc", WHITELIST, "createdAt", false).descending()).isTrue();
    }

    /** 白名单是防注入的第一道闸:未知字段必须报错,不能静默忽略。 */
    @Test
    void fieldOutsideWhitelistIsRejected() {
        assertThatThrownBy(() -> SortSpec.parse("passwordHash,asc", WHITELIST, "createdAt", true))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(CommonErrorCode.COMMON_SORT_FIELD_NOT_ALLOWED));

        assertThatThrownBy(() -> SortSpec.parse(",asc", WHITELIST, "createdAt", true))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(CommonErrorCode.COMMON_SORT_FIELD_NOT_ALLOWED));
    }

    @Test
    void invalidDirectionIsRejected() {
        assertThatThrownBy(() -> SortSpec.parse("name,sideways", WHITELIST, "createdAt", true))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(CommonErrorCode.COMMON_VALIDATION_FAILED));
    }

    @Test
    void tooManyPartsRejected() {
        assertThatThrownBy(() -> SortSpec.parse("name,asc,extra", WHITELIST, "createdAt", true))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(CommonErrorCode.COMMON_VALIDATION_FAILED));
    }
}
