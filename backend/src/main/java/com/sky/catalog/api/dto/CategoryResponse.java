package com.sky.catalog.api.dto;

import java.time.OffsetDateTime;

import com.sky.catalog.domain.Category;
import com.sky.common.util.Times;

/** 对外的分类。对应 openapi 的 {@code Category}。 */
public record CategoryResponse(
        Long id,
        String name,
        String type,
        Integer sortOrder,
        Integer status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getType() == null ? null : category.getType().name(),
                category.getSortOrder(),
                category.getStatus() == null ? null : category.getStatus().getValue(),
                Times.toOffset(category.getCreatedAt()),
                Times.toOffset(category.getUpdatedAt()));
    }
}
