package com.sky.insights.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import com.sky.common.error.BusinessException;
import com.sky.insights.domain.InsightsErrorCode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DateRangeValidatorTest {

    private final DateRangeValidator validator = new DateRangeValidator();

    @Test
    void bothMissingIsFine() {
        assertThatCode(() -> validator.validate(null, null)).doesNotThrowAnyException();
    }

    /** 契约 §2.4:只给一半 → 区间非法。 */
    @Test
    void halfGivenRangeIsRejected() {
        assertThatThrownBy(() -> validator.validate(LocalDate.of(2025, 1, 1), null))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(InsightsErrorCode.REPORT_DATE_RANGE_INVALID));
        assertThatThrownBy(() -> validator.validate(null, LocalDate.of(2025, 1, 1)))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(InsightsErrorCode.REPORT_DATE_RANGE_INVALID));
    }

    @Test
    void reversedRangeIsRejected() {
        assertThatThrownBy(() -> validator.validate(LocalDate.of(2025, 1, 2), LocalDate.of(2025, 1, 1)))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.errorCode()).isEqualTo(InsightsErrorCode.REPORT_DATE_RANGE_INVALID);
                    assertThat(ex.details()).isNotEmpty();
                });
    }

    @Test
    void singleDayAndFullLeapYearAreAllowed() {
        assertThatCode(() -> validator.validate(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 1)))
                .doesNotThrowAnyException();
        // 含首含尾:2024 是闰年,1/1 → 12/31 正好 366 天
        assertThatCode(() -> validator.validate(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31)))
                .doesNotThrowAnyException();
    }

    @Test
    void rangeLongerThan366DaysIsRejected() {
        assertThatThrownBy(() -> validator.validate(LocalDate.of(2024, 1, 1), LocalDate.of(2025, 1, 1)))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(InsightsErrorCode.REPORT_DATE_RANGE_TOO_LARGE));
        assertThat(DateRangeValidator.MAX_RANGE_DAYS).isEqualTo(366);
    }
}
