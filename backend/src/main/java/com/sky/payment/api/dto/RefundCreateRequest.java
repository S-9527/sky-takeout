package com.sky.payment.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.sky.payment.domain.RefundReasonType;

/**
 * 管理端发起退款(整单全额退)。
 *
 * <p>契约里原本只有 reason / reasonType / expectedAmountCents,**没有订单标识**——那样接口无法知道退哪一单。
 * 这里补上必填的 {@code orderNo}(顾客可见的业务单号,管理员手上就是这个),并写回 openapi。
 */
public record RefundCreateRequest(
        @NotBlank(message = "订单号不能为空")
        @Size(max = 32, message = "订单号最长 32 位")
        String orderNo,

        @NotBlank(message = "退款原因不能为空")
        @Size(max = 255, message = "退款原因最长 255 位")
        String reason,

        RefundReasonType reasonType,

        Long expectedAmountCents
) {
}
