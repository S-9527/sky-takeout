package com.sky.catalog.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 新增菜品。对应 openapi 的 {@code DishCreateRequest}。
 *
 * <p>{@code status} 不传默认停售(数据库默认值 0):上架必须是显式操作。
 */
public record DishCreateRequest(
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

        Integer status,

        Integer sortOrder,

        @Valid
        List<DishFlavorRequest> flavors
) {
}
