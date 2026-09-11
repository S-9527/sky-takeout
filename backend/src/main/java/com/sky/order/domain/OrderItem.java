package com.sky.order.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

import com.sky.common.persistence.AuditableEntity;
import com.sky.common.util.Json;

/**
 * 订单明细。**不可变**:这是全系统唯一允许(也要求)存快照的地方(领域文档 §3.9 / D5)。
 *
 * <p>订单是法律/财务凭证,必须能还原下单那一刻的真相,所以这里存名称、图片、单价、数量、口味与
 * 套餐组成;{@code dishId}/{@code setmealId} 只是溯源引用,商品被真删后会被置 NULL(外键 SET NULL),
 * 明细靠快照继续存活。
 */
@Getter
@Setter
@TableName("order_item")
public class OrderItem extends AuditableEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private ItemType itemType;

    /** 原菜品引用,商品被删除后为 NULL。 */
    private Long dishId;

    private Long setmealId;

    private String nameSnapshot;

    private String imageSnapshot;

    private Long unitPriceCents;

    private Integer quantity;

    /** = 单价 × 数量。 */
    private Long amountCents;

    /** 下单时选中的口味(JSON 数组),菜品无口味时为 NULL。 */
    @TableField("flavor_snapshot")
    private String flavorSnapshotJson;

    /** 套餐所含菜品明细快照(JSON 数组),菜品行为 NULL。 */
    @TableField("combo_snapshot")
    private String comboSnapshotJson;

    public List<FlavorChoice> getFlavorSnapshot() {
        return Json.readList(flavorSnapshotJson, FlavorChoice.class);
    }

    public void setFlavorSnapshot(List<FlavorChoice> flavors) {
        this.flavorSnapshotJson = flavors == null || flavors.isEmpty() ? null : Json.write(flavors);
    }

    public List<ComboSnapshotItem> getComboSnapshot() {
        return Json.readList(comboSnapshotJson, ComboSnapshotItem.class);
    }

    public void setComboSnapshot(List<ComboSnapshotItem> combo) {
        this.comboSnapshotJson = combo == null || combo.isEmpty() ? null : Json.write(combo);
    }
}
