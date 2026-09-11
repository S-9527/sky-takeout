package com.sky.shop.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 切换营业状态 / 公告 / 营业时间。对应 openapi 的 {@code ShopStatusUpdateRequest}。
 *
 * <p>{@code isOpen} 必填;{@code openTime}/{@code closeTime}/{@code notice} 为 {@code null} 表示保持原值。
 *
 * <p>营业时间**刻意不加** {@code @Pattern}:契约给这个端点的错误码是
 * {@code SHOP_BUSINESS_HOURS_INVALID},而 Bean Validation 失败会被统一成
 * {@code COMMON_VALIDATION_FAILED}。格式校验因此放在 service 里,换一个契约要求的错误码。
 */
public record ShopStatusUpdateRequest(
        @JsonProperty("isOpen")
        @NotNull(message = "isOpen 不能为空")
        Boolean isOpen,

        String openTime,

        String closeTime,

        @Size(max = 255, message = "公告最长 255 个字符")
        String notice
) {
}
