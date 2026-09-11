package com.sky.common.util;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;

/**
 * 时间转换的唯一入口。
 *
 * <p>约定:数据库与实体用 {@link LocalDateTime}(容器时区固定 Asia/Shanghai),
 * 对外 DTO 用 {@link OffsetDateTime},JSON 形如 {@code 2025-01-01T12:00:00+08:00}。
 * 转换只允许发生在这里,不要在业务代码里散落 {@code ZoneId.of(...)}。
 *
 * <p>{@code TIME} 列(营业时间)是例外:它是"墙上时钟"而不是时间点,所以走
 * {@link LocalTime} + {@link #TIME_FORMATTER},不套时区。
 */
public final class Times {

    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    /** 对外输出给前端的日期格式。 */
    public static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** {@code TIME} 列对外输出格式:{@code HH:mm:ss}。 */
    public static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    /** 入参接受的营业时间格式:{@code HH:mm} 或 {@code HH:mm:ss},与 openapi 的 pattern 一致。 */
    private static final DateTimeFormatter TIME_INPUT = new DateTimeFormatterBuilder()
            .appendPattern("HH:mm")
            .optionalStart().appendLiteral(':').appendPattern("ss").optionalEnd()
            .toFormatter();

    private Times() {
    }

    /** 实体时间 → 对外时间。null 安全。 */
    public static OffsetDateTime toOffset(LocalDateTime local) {
        return local == null ? null : local.atZone(ZONE).toOffsetDateTime();
    }

    /** 对外时间 → 实体时间。null 安全。 */
    public static LocalDateTime toLocal(OffsetDateTime offset) {
        return offset == null ? null : offset.atZoneSameInstant(ZONE).toLocalDateTime();
    }

    /** 当前时间(带偏移),用于生成对外字段。 */
    public static OffsetDateTime now() {
        return OffsetDateTime.now(ZONE);
    }

    /** 当前本地时间,用于写库。 */
    public static LocalDateTime nowLocal() {
        return LocalDateTime.now(ZONE);
    }

    /** {@code TIME} 列 → 对外字符串 {@code HH:mm:ss}。null 安全。 */
    public static String formatTime(LocalTime time) {
        return time == null ? null : time.format(TIME_FORMATTER);
    }

    /**
     * 对外字符串 → {@code LocalTime},接受 {@code HH:mm} 与 {@code HH:mm:ss}。
     *
     * <p>格式非法抛 {@link java.time.format.DateTimeParseException};是否翻译成业务错误码由调用方决定
     * (同一个格式问题在不同接口上可能对应不同的错误码)。
     */
    public static LocalTime parseTime(String text) {
        return LocalTime.parse(text, TIME_INPUT);
    }
}
