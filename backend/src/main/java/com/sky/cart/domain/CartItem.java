package com.sky.cart.domain;

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
 * 购物车行。**刻意不冗余商品名称/图片/价格**(D4):读取时联表取实时值。
 *
 * <p>旧实现把 name/image/amount 快照进购物车,商家改价后购物车仍显示旧价、下单时价格又变,
 * 是真实的资损与投诉来源。购物车是"意图",不是"订单",不该有快照;真正的快照只发生在下单那一刻。
 *
 * <p>表里还有两个 {@code VIRTUAL} 生成列 {@code dish_ref_id}/{@code setmeal_ref_id}
 * (供唯一键把 NULL 归一化成 0),由 MySQL 计算、应用层不可写,所以这里不映射它们。
 */
@Getter
@Setter
@TableName("cart_item")
public class CartItem extends AuditableEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long customerId;

    private ItemType itemType;

    /** {@code itemType=DISH} 时必填,另一个必为 NULL(库里有 CHECK 兜底)。 */
    private Long dishId;

    /** {@code itemType=SETMEAL} 时必填。 */
    private Long setmealId;

    private Integer quantity;

    /** 数据库列 {@code flavor_choice}(JSON,可空)。外部只通过 getFlavorChoice/setFlavorChoice 访问。 */
    @TableField("flavor_choice")
    private String flavorChoiceJson;

    /** {@link FlavorKeys} 算出的归一化键,未选口味为固定空串;唯一键的一部分。 */
    private String flavorKey;

    public List<FlavorChoice> getFlavorChoice() {
        return Json.readList(flavorChoiceJson, FlavorChoice.class);
    }

    /** 未选口味时写 NULL(与列的"未选为 NULL"约定一致),而不是空数组。 */
    public void setFlavorChoice(List<FlavorChoice> choices) {
        this.flavorChoiceJson = choices == null || choices.isEmpty() ? null : Json.write(choices);
    }
}
