package com.sky.catalog.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 新增套餐。对应 openapi 的 {@code SetmealCreateRequest}。
 *
 * <p>{@code items} 刻意不加 {@code @NotEmpty}:组成明细为空是**业务规则**违反,
 * 契约给的是 422 {@code SETMEAL_ITEMS_EMPTY};加了 Bean Validation 会变成 400
 * {@code COMMON_VALIDATION_FAILED},那个错误码就永远不可达了。
 */
public record SetmealCreateRequest(
        @NotNull(message = "分类不能为空")
        Long categoryId,

        @NotBlank(message = "套餐名称不能为空")
        @Size(max = 64, message = "套餐名称最长 64 位")
        String name,

        @NotNull(message = "价格不能为空")
        @Min(value = 0, message = "价格不能为负")
        Long priceCents,

        @Size(max = 255, message = "图片地址过长")
        String imageUrl,

        @Size(max = 255, message = "描述最长 255 位")
        String description,

        Integer status,

        @Valid
        List<SetmealItemRequest> items
) {
}
