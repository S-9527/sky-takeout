package com.sky.insights.api.dto;

import java.time.LocalDate;
import java.util.List;

import com.sky.insights.service.InsightsService;

/** 订单统计。对应 openapi 的 {@code OrderStats}。 */
public record OrderStatsResponse(LocalDate beginDate, LocalDate endDate, long totalOrderCount, long validOrderCount,
                                 double validOrderRate, List<OrderStatsItemResponse> daily) {

    public static OrderStatsResponse from(InsightsService.OrderStats stats) {
        return new OrderStatsResponse(stats.beginDate(), stats.endDate(), stats.totalOrderCount(),
                stats.validOrderCount(), stats.validOrderRate(),
                stats.daily().stream().map(OrderStatsItemResponse::from).toList());
    }
}
