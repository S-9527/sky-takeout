package com.sky.common.domain;

import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;
import java.util.function.Function;

/**
 * 统一分页出参。形状固定为 {@code {records, page, pageSize, total}}。
 */
public record PageResponse<T>(List<T> records, long page, long pageSize, long total) {

    public static <T> PageResponse<T> of(List<T> records, long page, long pageSize, long total) {
        return new PageResponse<>(records, page, pageSize, total);
    }

    /** 直接转换 MyBatis-Plus 分页结果。 */
    public static <T> PageResponse<T> from(IPage<T> page) {
        return new PageResponse<>(page.getRecords(), page.getCurrent(), page.getSize(), page.getTotal());
    }

    /** 转换分页结果并把每条记录映射为对外 DTO。 */
    public static <E, T> PageResponse<T> from(IPage<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getRecords().stream().map(mapper).toList(),
                page.getCurrent(),
                page.getSize(),
                page.getTotal());
    }

    public static <T> PageResponse<T> empty(long page, long pageSize) {
        return new PageResponse<>(List.of(), page, pageSize, 0);
    }
}
