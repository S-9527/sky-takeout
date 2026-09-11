package com.sky.common.domain;

import java.util.Set;

import com.sky.common.error.BusinessException;
import com.sky.common.error.CommonErrorCode;

/**
 * 排序意图。字段白名单在这里强制,不在控制器里各写一遍。
 *
 * <p>为什么是"白名单 + 报错"而不是"忽略未知字段":排序字段最终会拼进 SQL,
 * 白名单是防注入与防全表扫描的第一道闸;而静默忽略会让前端以为排序生效、实际没有,
 * 这种"看起来对了"的缺陷最难排查。
 *
 * @param field      已通过白名单校验的字段名(对应 API 里的 camelCase 名)
 * @param descending 是否降序
 */
public record SortSpec(String field, boolean descending) {

    public static final String ASC = "asc";
    public static final String DESC = "desc";

    public boolean ascending() {
        return !descending;
    }

    /**
     * 解析 {@code ?sort=field,asc|desc}。
     *
     * @param raw              原始参数,可为 null / 空
     * @param whitelist        该接口允许的字段名
     * @param defaultField     未传时的默认字段
     * @param defaultDescending 未传时的默认方向
     * @throws BusinessException 字段不在白名单(400 COMMON_SORT_FIELD_NOT_ALLOWED)或方向非法(400 COMMON_VALIDATION_FAILED)
     */
    public static SortSpec parse(String raw, Set<String> whitelist, String defaultField, boolean defaultDescending) {
        if (raw == null || raw.isBlank()) {
            return new SortSpec(defaultField, defaultDescending);
        }
        String[] parts = raw.split(",", -1);
        String field = parts[0].trim();
        if (field.isEmpty() || !whitelist.contains(field)) {
            throw new BusinessException(CommonErrorCode.COMMON_SORT_FIELD_NOT_ALLOWED);
        }
        boolean descending = defaultDescending;
        if (parts.length > 1) {
            String direction = parts[1].trim().toLowerCase();
            switch (direction) {
                case ASC -> descending = false;
                case DESC -> descending = true;
                default -> throw new BusinessException(CommonErrorCode.COMMON_VALIDATION_FAILED,
                        "排序方向只能是 asc 或 desc");
            }
        }
        if (parts.length > 2) {
            throw new BusinessException(CommonErrorCode.COMMON_VALIDATION_FAILED, "排序参数格式为 字段,方向");
        }
        return new SortSpec(field, descending);
    }
}
