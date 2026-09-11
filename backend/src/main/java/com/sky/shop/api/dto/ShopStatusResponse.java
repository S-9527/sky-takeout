package com.sky.shop.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;

import com.sky.common.util.Times;
import com.sky.shop.domain.ShopStatus;

/**
 * 管理端看到的营业状态。对应 openapi 的 {@code ShopStatus}。
 *
 * <p>{@code isOpen} 显式标注 JSON 名:Jackson 的 bean 命名会把布尔访问器 {@code isOpen()} 的
 * {@code is} 前缀当作 getter 前缀剥掉,不标注就会输出成 {@code open},与契约不符。
 */
public record ShopStatusResponse(
        @JsonProperty("isOpen") boolean isOpen,
        String openTime,
        String closeTime,
        String notice,
        OffsetDateTime updatedAt
) {

    public static ShopStatusResponse from(ShopStatus status) {
        return new ShopStatusResponse(
                status.isOpen(),
                Times.formatTime(status.getOpenTime()),
                Times.formatTime(status.getCloseTime()),
                status.getNotice(),
                Times.toOffset(status.getUpdatedAt()));
    }
}
