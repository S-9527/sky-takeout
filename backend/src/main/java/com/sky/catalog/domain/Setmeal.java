package com.sky.catalog.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import com.sky.common.domain.EnableStatus;
import com.sky.common.persistence.AuditableEntity;

/**
 * 套餐。套餐是**独立商品**而不是"菜品打折组合":有自己的图片、描述、手工定价(领域文档 §3.6)。
 *
 * <p>两条跨行规则由应用层保证(库里只提供支撑索引):
 * <ul>
 *   <li>定价 ≤ 所含菜品单价×份数之和,不允许凭空加价;</li>
 *   <li>起售的前提是所含菜品全部处于起售状态。</li>
 * </ul>
 * 停售菜品会连带停售相关套餐,那条规则在 {@code DishMapper#disableSetmealsContaining} 一侧实现。
 */
@Getter
@Setter
@TableName("setmeal")
public class Setmeal extends AuditableEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long categoryId;

    private String name;

    /** 手工定价,**单位:分**。 */
    private Long priceCents;

    private String imageUrl;

    private String description;

    /** 1 起售 / 0 停售。新建默认停售(数据库默认值)。 */
    private EnableStatus status;
}
