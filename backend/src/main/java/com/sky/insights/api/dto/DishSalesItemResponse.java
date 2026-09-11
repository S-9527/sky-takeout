package com.sky.insights.api.dto;

import com.sky.insights.service.InsightsService;

/** 单个菜品的销量条目。对应 openapi 的 {@code DishSalesItem}。 */
public record DishSalesItemResponse(int rank, String name, long copies) {

    public static DishSalesItemResponse from(InsightsService.TopDish dish) {
        return new DishSalesItemResponse(dish.rank(), dish.name(), dish.copies());
    }
}
