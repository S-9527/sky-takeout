package com.sky.insights.api.dto;

import java.time.LocalDate;

import com.sky.insights.service.InsightsService;

/** 单日订单明细。对应 openapi 的 {@code OrderStatsItem}。 */
public record OrderStatsItemResponse(LocalDate date, long totalOrderCount, long validOrderCount) {

    public static OrderStatsItemResponse from(InsightsService.OrderDay day) {
        return new OrderStatsItemResponse(day.date(), day.totalOrderCount(), day.validOrderCount());
    }
}
