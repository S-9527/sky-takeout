package com.sky.order.domain;

/** 套餐组成在下单那一刻的快照:菜品 id、名称、份数。 */
public record ComboSnapshotItem(Long dishId, String name, Integer copies) {
}
