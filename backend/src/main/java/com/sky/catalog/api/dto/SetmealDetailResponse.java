package com.sky.catalog.api.dto;

import java.time.OffsetDateTime;
import java.util.List;

import com.sky.catalog.domain.Setmeal;
import com.sky.common.util.Times;

/** 套餐详情:套餐字段 + 所含菜品。对应 openapi 的 {@code SetmealDetail}({@code Setmeal} 的 allOf)。 */
public record SetmealDetailResponse(
        Long id,
        Long categoryId,
        String categoryName,
        String name,
        Long priceCents,
        String imageUrl,
        String description,
        Integer status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<SetmealItemResponse> items
) {

    public static SetmealDetailResponse from(Setmeal setmeal, String categoryName, List<SetmealItemResponse> items) {
        return new SetmealDetailResponse(
                setmeal.getId(),
                setmeal.getCategoryId(),
                categoryName,
                setmeal.getName(),
                setmeal.getPriceCents(),
                setmeal.getImageUrl(),
                setmeal.getDescription(),
                setmeal.getStatus() == null ? null : setmeal.getStatus().getValue(),
                Times.toOffset(setmeal.getCreatedAt()),
                Times.toOffset(setmeal.getUpdatedAt()),
                items);
    }
}
