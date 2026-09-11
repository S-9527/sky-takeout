package com.sky.order.api.dto;

import java.time.OffsetDateTime;
import java.util.List;

import com.sky.common.util.Times;
import com.sky.order.domain.Order;

/**
 * 订单详情 = 订单 + 不可变明细。对应 openapi 的 OrderDetail(Order 的 allOf)。
 *
 * <p>字段是摊平的(而不是嵌套一个 OrderResponse),因为契约的形状就是扁平的。
 * payments/refunds 属于支付上下文,随支付切片一起补齐。
 */
public record OrderDetailResponse(
        Long id,
        String orderNo,
        Long customerId,
        String status,
        Long totalAmountCents,
        Long packAmountCents,
        Long deliveryAmountCents,
        Long discountAmountCents,
        Long payAmountCents,
        String payStatus,
        String payMethod,
        String consignee,
        String phone,
        String province,
        String city,
        String district,
        String detail,
        Long sourceAddressId,
        String remark,
        Integer tablewareCount,
        Integer itemCount,
        OffsetDateTime placedAt,
        OffsetDateTime estimatedDeliveryAt,
        OffsetDateTime paidAt,
        OffsetDateTime acceptedAt,
        OffsetDateTime deliveringAt,
        OffsetDateTime completedAt,
        OffsetDateTime cancelledAt,
        String cancelSide,
        String cancelReason,
        List<OrderItemResponse> items
) {

    public static OrderDetailResponse from(Order order, List<OrderItemResponse> items) {
        return new OrderDetailResponse(
                order.getId(),
                order.getOrderNo(),
                order.getCustomerId(),
                order.getStatus() == null ? null : order.getStatus().name(),
                order.getTotalAmountCents(),
                order.getPackAmountCents(),
                order.getDeliveryAmountCents(),
                order.getDiscountAmountCents(),
                order.getPayAmountCents(),
                order.getPayStatus() == null ? null : order.getPayStatus().name(),
                order.getPayMethod() == null ? null : order.getPayMethod().name(),
                order.getConsignee(),
                order.getPhone(),
                order.getProvince(),
                order.getCity(),
                order.getDistrict(),
                order.getDetail(),
                order.getSourceAddressId(),
                order.getRemark(),
                order.getTablewareCount(),
                order.getItemCount(),
                Times.toOffset(order.getPlacedAt()),
                Times.toOffset(order.getEstimatedDeliveryAt()),
                Times.toOffset(order.getPaidAt()),
                Times.toOffset(order.getAcceptedAt()),
                Times.toOffset(order.getDeliveringAt()),
                Times.toOffset(order.getCompletedAt()),
                Times.toOffset(order.getCancelledAt()),
                order.getCancelSide() == null ? null : order.getCancelSide().name(),
                order.getCancelReason(), items);
    }
}
