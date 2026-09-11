package com.sky.order.api.dto;

/** 试算请求。对应 openapi 的 {@code OrderPreviewRequest};不传地址时用默认地址。 */
public record OrderPreviewRequest(Long addressId) {
}
