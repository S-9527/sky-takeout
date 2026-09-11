package com.sky.catalog.api.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 批量起售 / 停售。对应 openapi 的 {@code BatchStatusRequest}。
 *
 * <p>上线 100 条:批量接口不限量等于给了一个"一次改全表"的入口。
 */
public record BatchStatusRequest(
        @NotEmpty(message = "ids 不能为空")
        @Size(max = 100, message = "单次最多 100 条")
        List<Long> ids,

        @NotNull(message = "status 不能为空")
        Integer status
) {
}
