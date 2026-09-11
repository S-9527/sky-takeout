package com.sky.common.domain;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PageResponseTest {

    @Test
    void fromIPageKeepsPaginationMetadata() {
        Page<String> page = new Page<>(2, 10);
        page.setRecords(List.of("a", "b"));
        page.setTotal(42);

        PageResponse<String> response = PageResponse.from(page);

        assertThat(response.records()).containsExactly("a", "b");
        assertThat(response.page()).isEqualTo(2);
        assertThat(response.pageSize()).isEqualTo(10);
        assertThat(response.total()).isEqualTo(42);
    }

    @Test
    void fromIPageWithMapperTransformsRecordsButNotMetadata() {
        Page<Integer> page = new Page<>(1, 5);
        page.setRecords(List.of(1, 2, 3));
        page.setTotal(3);

        PageResponse<String> response = PageResponse.from(page, i -> "n" + i);

        assertThat(response.records()).containsExactly("n1", "n2", "n3");
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.pageSize()).isEqualTo(5);
        assertThat(response.total()).isEqualTo(3);
    }

    @Test
    void mapKeepsPaginationMetadata() {
        PageResponse<Integer> source = PageResponse.of(List.of(1, 2), 3, 20, 100);

        PageResponse<String> mapped = source.map(i -> "v" + i);

        assertThat(mapped.records()).containsExactly("v1", "v2");
        assertThat(mapped.page()).isEqualTo(3);
        assertThat(mapped.pageSize()).isEqualTo(20);
        assertThat(mapped.total()).isEqualTo(100);
    }

    @Test
    void emptyHasNoRecordsButKeepsRequestedPaging() {
        PageResponse<String> empty = PageResponse.empty(4, 50);

        assertThat(empty.records()).isEmpty();
        assertThat(empty.page()).isEqualTo(4);
        assertThat(empty.pageSize()).isEqualTo(50);
        assertThat(empty.total()).isZero();
    }
}
