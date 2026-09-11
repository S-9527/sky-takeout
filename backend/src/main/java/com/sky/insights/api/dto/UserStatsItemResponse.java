package com.sky.insights.api.dto;

import java.time.LocalDate;

import com.sky.insights.service.InsightsService;

/** 单日用户明细。对应 openapi 的 {@code UserStatsItem}。 */
public record UserStatsItemResponse(LocalDate date, long newUserCount, long totalUserCount) {

    public static UserStatsItemResponse from(InsightsService.UserDay day) {
        return new UserStatsItemResponse(day.date(), day.newUserCount(), day.totalUserCount());
    }
}
