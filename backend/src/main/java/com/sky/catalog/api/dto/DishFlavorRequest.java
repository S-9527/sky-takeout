package com.sky.catalog.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 口味配置入参。对应 openapi 的 {@code DishFlavor}。
 *
 * <p>{@code id} 不在请求体里:编辑时口味是**整体替换**的,旧的 id 没有意义。
 */
public record DishFlavorRequest(
        @NotBlank(message = "口味维度名不能为空")
        @Size(max = 32, message = "口味维度名最长 32 位")
        String name,

        @NotEmpty(message = "口味选项至少一个")
        @Size(max = 32, message = "口味选项最多 32 个")
        List<@NotBlank(message = "口味选项不能为空") @Size(max = 32, message = "单个选项最长 32 位") String> options,

        Integer sortOrder
) {
}
