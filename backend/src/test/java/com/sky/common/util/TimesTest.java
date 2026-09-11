package com.sky.common.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TimesTest {

    @Test
    void nullSafeConversions() {
        assertThat(Times.toOffset(null)).isNull();
        assertThat(Times.toLocal(null)).isNull();
        assertThat(Times.formatTime(null)).isNull();
    }

    /** 库里的墙上时间按 Asia/Shanghai 解释,对外输出带 +08:00。 */
    @Test
    void localToOffsetUsesShanghaiZone() {
        OffsetDateTime offset = Times.toOffset(LocalDateTime.of(2025, 1, 1, 12, 0, 0));

        assertThat(offset.toString()).isEqualTo("2025-01-01T12:00+08:00");
    }

    @Test
    void offsetToLocalConvertsBackToTheSameWallClock() {
        OffsetDateTime offset = OffsetDateTime.parse("2025-01-01T12:00:00+08:00");

        assertThat(Times.toLocal(offset)).isEqualTo(LocalDateTime.of(2025, 1, 1, 12, 0));
    }

    @Test
    void nowUsesShanghaiZone() {
        assertThat(Times.now().getOffset()).isEqualTo(Times.ZONE.getRules().getOffset(java.time.Instant.now()));
        assertThat(Times.nowLocal()).isNotNull();
    }

    /** TIME 列是墙上时钟,不套时区,输出固定 HH:mm:ss。 */
    @Test
    void formatTimePadsSeconds() {
        assertThat(Times.formatTime(LocalTime.of(9, 0))).isEqualTo("09:00:00");
        assertThat(Times.formatTime(LocalTime.of(22, 5, 7))).isEqualTo("22:05:07");
    }

    @Test
    void parseTimeAcceptsBothContractFormats() {
        assertThat(Times.parseTime("08:30")).isEqualTo(LocalTime.of(8, 30));
        assertThat(Times.parseTime("08:30:15")).isEqualTo(LocalTime.of(8, 30, 15));
        assertThat(Times.parseTime("00:00")).isEqualTo(LocalTime.MIDNIGHT);
    }

    @Test
    void parseTimeRejectsAnythingElse() {
        assertThatThrownBy(() -> Times.parseTime("9am")).isInstanceOf(DateTimeParseException.class);
        assertThatThrownBy(() -> Times.parseTime("24:00")).isInstanceOf(DateTimeParseException.class);
        assertThatThrownBy(() -> Times.parseTime("08:60")).isInstanceOf(DateTimeParseException.class);
        assertThatThrownBy(() -> Times.parseTime("")).isInstanceOf(DateTimeParseException.class);
    }
}
