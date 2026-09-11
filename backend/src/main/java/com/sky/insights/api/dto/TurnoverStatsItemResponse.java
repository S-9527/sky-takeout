package com.sky.insights.api.dto;

import java.time.LocalDate;

import com.sky.insights.service.InsightsService;

/** 单日营业额明细。对应 openapi 的 {@code TurnoverStatsItem}。 */
public record TurnoverStatsItemResponse(LocalDate date, long revenueCents, long orderCount,
                                        long averageOrderCents) {

    public static TurnoverStatsItemResponse from(InsightsService.TurnoverDay day) {
        return new TurnoverStatsItemResponse(day.date(), day.revenueCents(), day.orderCount(),
                day.averageOrderCents());
    }
}
