package com.sky.catalog.api.dto;

import java.time.OffsetDateTime;

import com.sky.catalog.domain.Setmeal;
import com.sky.common.util.Times;

/** 对外的套餐列表项。对应 openapi 的 {@code Setmeal}。 */
public record SetmealResponse(
        Long id,
        Long categoryId,
        String categoryName,
        String name,
        Long priceCents,
        String imageUrl,
        String description,
        Integer status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public static SetmealResponse from(Setmeal setmeal, String categoryName) {
        return new SetmealResponse(
                setmeal.getId(),
                setmeal.getCategoryId(),
                categoryName,
                setmeal.getName(),
                setmeal.getPriceCents(),
                setmeal.getImageUrl(),
                setmeal.getDescription(),
                setmeal.getStatus() == null ? null : setmeal.getStatus().getValue(),
                Times.toOffset(setmeal.getCreatedAt()),
                Times.toOffset(setmeal.getUpdatedAt()));
    }
}
