package com.sky.catalog.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 编辑套餐。对应 openapi 的 {@code SetmealUpdateRequest}。
 *
 * <p>{@code items} 为 {@code null} 表示不改组成;传了则整体替换。定价与起售校验都按最终生效的组成算。
 */
public record SetmealUpdateRequest(
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

        @NotNull(message = "状态不能为空")
        Integer status,

        @Valid
        List<SetmealItemRequest> items
) {
}
