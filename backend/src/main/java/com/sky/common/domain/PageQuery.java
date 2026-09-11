package com.sky.common.domain;

/**
 * 统一分页入参。
 *
 * <p>约定:{@code page} 从 1 开始;{@code pageSize} 超过上限时**钳制**到上限而不是报错——
 * 前端误传一个大值不应该让整个列表页崩掉,但要防止有人用它拉全表。
 */
public record PageQuery(Integer page, Integer pageSize) {

    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    public PageQuery {
        page = (page == null || page < 1) ? DEFAULT_PAGE : page;
        pageSize = (pageSize == null || pageSize < 1) ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
    }

    public static PageQuery of(Integer page, Integer pageSize) {
        return new PageQuery(page, pageSize);
    }

    public long offset() {
        return (long) (page - 1) * pageSize;
    }
}
