package com.sky.order.api.dto;

import java.util.List;

import com.sky.order.service.OrderService;

/** 再来一单结果。对应 openapi 的 {@code ReorderResult}。 */
public record ReorderResultResponse(int addedCount, List<SkippedItemResponse> skippedItems) {

    public record SkippedItemResponse(String name, String reason) {
    }

    public static ReorderResultResponse from(OrderService.ReorderResult result) {
        return new ReorderResultResponse(result.addedCount(),
                result.skippedItems().stream()
                        .map(item -> new SkippedItemResponse(item.name(), item.reason()))
                        .toList());
    }
}
