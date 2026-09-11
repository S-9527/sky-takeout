package com.sky.shop.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.sky.common.util.Times;
import com.sky.shop.domain.ShopStatus;

/**
 * 顾客端看到的营业状态。对应 openapi 的 {@code ShopStatusView}:只有营业状态、营业时间与公告,
 * 不带任何审计字段——顾客不需要知道"是谁在几点改的营业状态"。
 */
public record ShopStatusViewResponse(
        @JsonProperty("isOpen") boolean isOpen,
        String openTime,
        String closeTime,
        String notice
) {

    public static ShopStatusViewResponse from(ShopStatus status) {
        return new ShopStatusViewResponse(
                status.isOpen(),
                Times.formatTime(status.getOpenTime()),
                Times.formatTime(status.getCloseTime()),
                status.getNotice());
    }
}
