package com.sky.catalog.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.sky.catalog.domain.CategoryType;

/**
 * 编辑分类。对应 openapi 的 {@code CategoryUpdateRequest}。
 *
 * <p>{@code type} 可选:不传表示"不改类型";传了必须与当前类型一致,否则 422
 * {@code CATEGORY_TYPE_IMMUTABLE}。把校验放在请求体里是刻意的——如果请求体根本没有这个字段,
 * 前端试图改类型时会静默成功(实际没改),而契约里的那个错误码永远不可达。
 */
public record CategoryUpdateRequest(
        @NotBlank(message = "分类名称不能为空")
        @Size(max = 32, message = "分类名称最长 32 位")
        String name,

        CategoryType type,

        @NotNull(message = "排序值不能为空")
        Integer sortOrder,

        @NotNull(message = "状态不能为空")
        Integer status
) {
}
