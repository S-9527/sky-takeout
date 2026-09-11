package com.sky.order.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 拒单原因。对应 openapi 的 {@code RejectOrderRequest};已支付订单会被强制退款(R6)。 */
public record RejectOrderRequest(
        @NotBlank(message = "拒单原因不能为空")
        @Size(max = 255, message = "拒单原因最长 255 位")
        String reason
) {
}
