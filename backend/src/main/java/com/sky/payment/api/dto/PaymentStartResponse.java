package com.sky.payment.api.dto;

import com.sky.payment.service.PaymentService;

/** 小程序支付参数。对应 openapi 的 {@code PaymentStartResponse}。 */
public record PaymentStartResponse(
        Long paymentId,
        String channel,
        String status,
        Long payAmountCents,
        String timeStamp,
        String nonceStr,
        String packageValue,
        String signType,
        String paySign,
        String mockPayUrl
) {

    public static PaymentStartResponse from(PaymentService.StartResult result) {
        return new PaymentStartResponse(result.paymentId(), result.channel(), result.status(),
                result.payAmountCents(), result.timeStamp(), result.nonceStr(), result.packageValue(),
                result.signType(), result.paySign(), result.mockPayUrl());
    }
}
