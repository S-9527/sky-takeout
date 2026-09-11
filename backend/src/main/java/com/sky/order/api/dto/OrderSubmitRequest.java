package com.sky.order.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 下单请求。对应 openapi 的 {@code OrderSubmitRequest}。
 *
 * <p>请求体里**没有任何金额字段参与计算**(R5):{@code expectedTotalAmountCents} 只用于价格变动检测。
 */
public record OrderSubmitRequest(
        @NotNull(message = "收货地址不能为空")
        Long addressId,

        @Size(max = 255, message = "备注最长 255 位")
        String remark,

        @Min(value = 0, message = "餐具份数不能为负")
        @Max(value = 20, message = "餐具份数最多 20")
        Integer tablewareCount,

        @Min(value = 0, message = "金额不能为负")
        Long expectedTotalAmountCents
) {
}
