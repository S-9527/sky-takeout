package com.sky.catalog.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 编辑菜品。对应 openapi 的 {@code DishUpdateRequest}。
 *
 * <p>{@code flavors} 为 {@code null} 表示不改口味配置;传了(含空数组)则整体替换。
 * 这两种语义在 JSON 上可区分:字段缺省/显式 null 对 null,{@code []} 对空列表。
 */
public record DishUpdateRequest(
        @NotNull(message = "分类不能为空")
        Long categoryId,

        @NotBlank(message = "菜品名称不能为空")
        @Size(max = 64, message = "菜品名称最长 64 位")
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

        Integer sortOrder,

        @Valid
        List<DishFlavorRequest> flavors
) {
}
