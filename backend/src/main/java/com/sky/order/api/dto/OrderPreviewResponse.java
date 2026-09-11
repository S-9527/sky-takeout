package com.sky.order.api.dto;

import java.time.OffsetDateTime;
import java.util.List;

import com.sky.order.service.OrderService;

/**
 * 试算结果。对应 openapi 的 {@code OrderPreview}({@code OrderAmounts} 的 allOf),
 * 所以金额字段在这里摊平。
 *
 * <p>{@code shopOpen=false} 时前端应禁用提交按钮(R1 只约束下单,试算本身仍然可用)。
 */
public record OrderPreviewResponse(
        Long totalAmountCents,
        Long packAmountCents,
        Long deliveryAmountCents,
        Long discountAmountCents,
        Long payAmountCents,
        List<PreviewOrderItemResponse> items,
        DeliveryAddressResponse address,
        OffsetDateTime estimatedDeliveryAt,
        boolean shopOpen
) {

    public static OrderPreviewResponse from(OrderService.PreviewResult preview) {
        return new OrderPreviewResponse(
                preview.amounts().totalAmountCents(),
                preview.amounts().packAmountCents(),
                preview.amounts().deliveryAmountCents(),
                preview.amounts().discountAmountCents(),
                preview.amounts().payAmountCents(),
                preview.items().stream().map(PreviewOrderItemResponse::from).toList(),
                DeliveryAddressResponse.from(preview.address()),
                OrderService.estimatedDeliveryAt() == null
                        ? null
                        : com.sky.common.util.Times.toOffset(OrderService.estimatedDeliveryAt()),
                preview.shopOpen());
    }
}
