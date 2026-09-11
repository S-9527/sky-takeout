package com.sky.insights.api.dto;

import java.time.LocalDate;
import java.util.List;

import com.sky.insights.service.InsightsService;

/** 营业额统计。对应 openapi 的 {@code TurnoverStats}。 */
public record TurnoverStatsResponse(LocalDate beginDate, LocalDate endDate, long sum,
                                    List<TurnoverStatsItemResponse> daily) {

    public static TurnoverStatsResponse from(InsightsService.TurnoverStats stats) {
        return new TurnoverStatsResponse(stats.beginDate(), stats.endDate(), stats.sum(),
                stats.daily().stream().map(TurnoverStatsItemResponse::from).toList());
    }
}
