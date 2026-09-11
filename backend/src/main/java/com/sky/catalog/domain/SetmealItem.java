package com.sky.catalog.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import com.sky.common.persistence.AuditableEntity;

/**
 * 套餐组成行。{@code (setmeal_id, dish_id)} 唯一——同一菜品在套餐里只能出现一次,份数用 {@code copies} 表达。
 *
 * <p>外键:{@code setmeal_id} 级联删除(删套餐连带删组成),{@code dish_id} 限制删除
 * (被套餐引用的菜品不可删)。这张表没有审计之外的状态列。
 */
@Getter
@Setter
@TableName("setmeal_item")
public class SetmealItem extends AuditableEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long setmealId;

    private Long dishId;

    /** 份数,≥1。 */
    private Integer copies;
}
