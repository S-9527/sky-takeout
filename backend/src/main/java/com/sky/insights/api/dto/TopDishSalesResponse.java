package com.sky.insights.api.dto;

import java.util.List;

import com.sky.insights.service.InsightsService;

/** 销量排行。对应 openapi 的 {@code TopDishSales}。 */
public record TopDishSalesResponse(int topNumber, List<DishSalesItemResponse> items) {

    public static TopDishSalesResponse from(InsightsService.TopDishSales sales) {
        return new TopDishSalesResponse(sales.topNumber(),
                sales.items().stream().map(DishSalesItemResponse::from).toList());
    }
}
