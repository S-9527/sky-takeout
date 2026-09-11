package com.sky.catalog.api.dto;

import java.time.OffsetDateTime;

import com.sky.catalog.domain.Dish;
import com.sky.common.util.Times;

/** 对外的菜品列表项(不含口味明细)。对应 openapi 的 {@code Dish}。 */
public record DishResponse(
        Long id,
        Long categoryId,
        String categoryName,
        String name,
        Long priceCents,
        String imageUrl,
        String description,
        Integer status,
        Integer sortOrder,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public static DishResponse from(Dish dish, String categoryName) {
        return new DishResponse(
                dish.getId(),
                dish.getCategoryId(),
                categoryName,
                dish.getName(),
                dish.getPriceCents(),
                dish.getImageUrl(),
                dish.getDescription(),
                dish.getStatus() == null ? null : dish.getStatus().getValue(),
                dish.getSortOrder(),
                Times.toOffset(dish.getCreatedAt()),
                Times.toOffset(dish.getUpdatedAt()));
    }
}
