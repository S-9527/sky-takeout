package com.sky.catalog.api.dto;

import java.time.OffsetDateTime;
import java.util.List;

import com.sky.catalog.domain.Dish;
import com.sky.common.util.Times;

/**
 * 菜品详情:菜品字段 + 口味配置。对应 openapi 的 {@code DishDetail}({@code Dish} 的 allOf)。
 *
 * <p>这里把字段摊平重复了一遍,而不是嵌套一个 {@code DishResponse}:契约里的形状是扁平的,
 * 嵌套会让生成的 TypeScript 类型与契约不符。
 */
public record DishDetailResponse(
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
        OffsetDateTime updatedAt,
        List<DishFlavorResponse> flavors
) {

    public static DishDetailResponse from(Dish dish, String categoryName, List<DishFlavorResponse> flavors) {
        return new DishDetailResponse(
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
                Times.toOffset(dish.getUpdatedAt()),
                flavors);
    }
}
