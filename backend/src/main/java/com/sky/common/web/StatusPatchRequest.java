package com.sky.common.web;

import jakarta.validation.constraints.NotNull;

/**
 * 启用 / 禁用类接口的统一请求体。员工、分类、菜品、套餐的状态切换共用。
 *
 * @param status 1 启用 / 0 禁用
 */
public record StatusPatchRequest(@NotNull(message = "status 不能为空") Integer status) {
}
