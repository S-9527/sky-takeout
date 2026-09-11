package com.sky.catalog.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** 套餐组成行入参。对应 openapi 的 {@code SetmealItem}。 */
public record SetmealItemRequest(
        @NotNull(message = "dishId 不能为空")
        Long dishId,

        @NotNull(message = "份数不能为空")
        @Min(value = 1, message = "份数至少为 1")
        Integer copies
) {
}
