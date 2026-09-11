package com.sky.order.api.dto;

import jakarta.validation.constraints.Size;

/** 催单附加说明,可空。对应 openapi 的 {@code OrderRemindRequest}。 */
public record OrderRemindRequest(
        @Size(max = 100, message = "催单说明最长 100 位")
        String message
) {
}
