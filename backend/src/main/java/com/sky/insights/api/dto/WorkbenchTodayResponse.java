package com.sky.insights.api.dto;

import com.sky.insights.service.InsightsService;

/** 工作台"今日"数据块。对应 openapi 的 {@code WorkbenchToday}。 */
public record WorkbenchTodayResponse(long turnoverCents, long validOrderCount, long totalOrderCount,
                                     long newUserCount, long pendingAcceptanceCount, long pendingDeliveryCount) {

    public static WorkbenchTodayResponse from(InsightsService.WorkbenchToday today) {
        return new WorkbenchTodayResponse(today.turnoverCents(), today.validOrderCount(), today.totalOrderCount(),
                today.newUserCount(), today.pendingAcceptanceCount(), today.pendingDeliveryCount());
    }
}
