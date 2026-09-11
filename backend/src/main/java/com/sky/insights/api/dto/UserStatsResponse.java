package com.sky.insights.api.dto;

import java.time.LocalDate;
import java.util.List;

import com.sky.insights.service.InsightsService;

/** 用户统计。对应 openapi 的 {@code UserStats}。 */
public record UserStatsResponse(LocalDate beginDate, LocalDate endDate, long newUserCount, long totalUserCount,
                                List<UserStatsItemResponse> daily) {

    public static UserStatsResponse from(InsightsService.UserStats stats) {
        return new UserStatsResponse(stats.beginDate(), stats.endDate(), stats.newUserCount(),
                stats.totalUserCount(), stats.daily().stream().map(UserStatsItemResponse::from).toList());
    }
}
