package com.sky.payment.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.sky.payment.domain.PaymentChannel;

/** 发起支付请求。对应 openapi 的 {@code PaymentCreateRequest}。 */
public record PaymentCreateRequest(
        @NotNull(message = "支付渠道不能为空")
        PaymentChannel channel,

        @Size(max = 64, message = "openid 过长")
        String payerOpenid
) {
}
