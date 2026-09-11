package com.sky.insights.api.dto;

import com.sky.insights.service.InsightsService;

/** 工作台概览里的单个计数指标。对应 openapi 的 {@code OverviewItem}。 */
public record OverviewItemResponse(String name, String title, long value) {

    public static OverviewItemResponse from(InsightsService.OverviewItem item) {
        return new OverviewItemResponse(item.name(), item.title(), item.value());
    }
}
