package com.sky.insights.api.dto;

import java.util.List;

import com.sky.insights.service.InsightsService;

/** 工作台概览。对应 openapi 的 {@code Workbench}。 */
public record WorkbenchResponse(WorkbenchTodayResponse today, List<OverviewItemResponse> orderOverview,
                                List<OverviewItemResponse> dishOverview) {

    public static WorkbenchResponse from(InsightsService.Workbench workbench) {
        return new WorkbenchResponse(WorkbenchTodayResponse.from(workbench.today()),
                workbench.orderOverview().stream().map(OverviewItemResponse::from).toList(),
                workbench.dishOverview().stream().map(OverviewItemResponse::from).toList());
    }
}
