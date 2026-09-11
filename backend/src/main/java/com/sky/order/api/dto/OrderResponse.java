package com.sky.order.api.dto;

import java.time.OffsetDateTime;

import com.sky.common.util.Times;
import com.sky.order.domain.Order;

/**
 * 对外的订单(列表项,含金额与地址快照,不含明细)。对应 openapi 的 Order。
 *
 * <p>所有时间字段都是 OffsetDateTime(+08:00),金额一律整数分(D1)。
 */
public record OrderResponse(
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
        String cancelReason
) {

    public static OrderResponse from(Order order) {
        return new OrderResponse(
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
                order.getCancelReason());
    }
}
