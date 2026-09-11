package com.sky.common.util;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 时间转换的唯一入口。
 *
 * <p>约定:数据库与实体用 {@link LocalDateTime}(容器时区固定 Asia/Shanghai),
 * 对外 DTO 用 {@link OffsetDateTime},JSON 形如 {@code 2025-01-01T12:00:00+08:00}。
 * 转换只允许发生在这里,不要在业务代码里散落 {@code ZoneId.of(...)}。
 */
public final class Times {

    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    /** 对外输出给前端的日期格式。 */
    public static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

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
}
