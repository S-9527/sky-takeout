package com.sky.order.api.dto;

import com.sky.order.domain.ComboSnapshotItem;

/** 套餐组成在下单那一刻的快照。对应 openapi 的 {@code ComboSnapshotItem}。 */
public record ComboSnapshotItemResponse(Long dishId, String name, Integer copies) {

    public static ComboSnapshotItemResponse from(ComboSnapshotItem item) {
        return new ComboSnapshotItemResponse(item.dishId(), item.name(), item.copies());
    }
}
