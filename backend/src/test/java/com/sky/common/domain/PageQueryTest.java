package com.sky.common.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PageQueryTest {

    @Test
    void nullFallsBackToDefaults() {
        PageQuery query = PageQuery.of(null, null);

        assertThat(query.page()).isEqualTo(PageQuery.DEFAULT_PAGE);
        assertThat(query.pageSize()).isEqualTo(PageQuery.DEFAULT_PAGE_SIZE);
    }

    @Test
    void nonPositiveValuesFallBackToDefaultsInsteadOfFailing() {
        assertThat(PageQuery.of(0, 0).page()).isEqualTo(PageQuery.DEFAULT_PAGE);
        assertThat(PageQuery.of(-3, -9).pageSize()).isEqualTo(PageQuery.DEFAULT_PAGE_SIZE);
    }

    /** 契约 §1.5:pageSize 超限是钳制,不是报错——但也不能让人一次拉全表。 */
    @Test
    void oversizedPageSizeIsClampedNotRejected() {
        assertThat(PageQuery.of(1, 9999).pageSize()).isEqualTo(PageQuery.MAX_PAGE_SIZE);
        assertThat(PageQuery.of(1, PageQuery.MAX_PAGE_SIZE).pageSize()).isEqualTo(PageQuery.MAX_PAGE_SIZE);
    }

    @Test
    void offsetIsZeroBased() {
        assertThat(PageQuery.of(1, 20).offset()).isZero();
        assertThat(PageQuery.of(3, 20).offset()).isEqualTo(40);
        assertThat(PageQuery.of(2, 100).offset()).isEqualTo(100);
    }
}
