package com.sky.catalog.api.dto;

import com.sky.catalog.service.SetmealService;

/** 对外的套餐组成行,含联表取到的菜品名与当前单价。对应 openapi 的 {@code SetmealItem}。 */
public record SetmealItemResponse(
        Long id,
        Long dishId,
        String dishName,
        Long dishPriceCents,
        Integer copies
) {

    public static SetmealItemResponse from(SetmealService.ItemView item) {
        return new SetmealItemResponse(
                item.id(), item.dishId(), item.dishName(), item.dishPriceCents(), item.copies());
    }
}
