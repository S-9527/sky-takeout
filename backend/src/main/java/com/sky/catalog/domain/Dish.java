package com.sky.catalog.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import com.sky.common.domain.EnableStatus;
import com.sky.common.persistence.AuditableEntity;

/**
 * 菜品。
 *
 * <p>价格用整数分({@code price_cents},D1);所属分类必须是 {@code type=DISH} 的分类,
 * 由应用层保证(数据库只有外键,管不了类型)。
 *
 * <p>被套餐引用的菜品**不可删除**:{@code setmeal_item} 的外键是 {@code ON DELETE RESTRICT},
 * 应用层把约束违例翻译成 422 {@code SETMEAL_CONTAINS_DISH}。
 * 但它可以被订单引用后删除——{@code order_item} 的外键是 {@code SET NULL},靠快照存活。
 */
@Getter
@Setter
@TableName("dish")
public class Dish extends AuditableEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long categoryId;

    private String name;

    /** 售价,**单位:分**。 */
    private Long priceCents;

    private String imageUrl;

    private String description;

    /** 1 起售 / 0 停售。新建默认停售(数据库默认值),上架是显式操作。 */
    private EnableStatus status;

    private Integer sortOrder;
}
