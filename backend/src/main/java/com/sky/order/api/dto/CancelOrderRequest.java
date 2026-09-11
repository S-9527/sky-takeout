package com.sky.order.api.dto;

import jakarta.validation.constraints.Size;

/** 取消原因,可空。对应 openapi 的 {@code CancelOrderRequest}。 */
public record CancelOrderRequest(
        @Size(max = 255, message = "取消原因最长 255 位")
        String reason
) {
}
