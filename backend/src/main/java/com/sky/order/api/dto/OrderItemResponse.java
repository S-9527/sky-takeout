package com.sky.order.api.dto;

import java.util.List;

import com.sky.order.domain.FlavorChoice;
import com.sky.order.domain.OrderItem;

/** 订单明细(不可变快照)。对应 openapi 的 {@code OrderItem}。 */
public record OrderItemResponse(
        Long id,
        String itemType,
        Long dishId,
        Long setmealId,
        String nameSnapshot,
        String imageSnapshot,
        Long unitPriceCents,
        Integer quantity,
        Long amountCents,
        List<FlavorChoice> flavorSnapshot,
        List<ComboSnapshotItemResponse> comboSnapshot
) {

    public static OrderItemResponse from(OrderItem item) {
        List<FlavorChoice> flavors = item.getFlavorSnapshot();
        List<ComboSnapshotItemResponse> combo = item.getComboSnapshot().stream()
                .map(ComboSnapshotItemResponse::from)
                .toList();
        return new OrderItemResponse(
                item.getId(),
                item.getItemType() == null ? null : item.getItemType().name(),
                item.getDishId(),
                item.getSetmealId(),
                item.getNameSnapshot(),
                item.getImageSnapshot(),
                item.getUnitPriceCents(),
                item.getQuantity(),
                item.getAmountCents(),
                flavors.isEmpty() ? null : flavors,
                combo.isEmpty() ? null : combo);
    }
}
