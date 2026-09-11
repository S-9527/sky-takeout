package com.sky.catalog.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.sky.catalog.domain.CategoryType;

/**
 * 新增分类。对应 openapi 的 {@code CategoryCreateRequest}。
 *
 * <p>{@code sortOrder} 不传默认 0;{@code status} 不传默认启用。
 */
public record CategoryCreateRequest(
        @NotBlank(message = "分类名称不能为空")
        @Size(max = 32, message = "分类名称最长 32 位")
        String name,

        @NotNull(message = "分类类型不能为空")
        CategoryType type,

        Integer sortOrder,

        Integer status
) {
}
