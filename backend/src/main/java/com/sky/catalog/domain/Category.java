package com.sky.catalog.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import com.sky.common.domain.EnableStatus;
import com.sky.common.persistence.AuditableEntity;

/**
 * 商品分类(菜品 / 套餐)。同类型下名称唯一,由 {@code uk_category_type_name} 兜底。
 *
 * <p>被菜品或套餐引用的分类**不可删除**:外键是 {@code ON DELETE RESTRICT},
 * 应用层把数据库约束违例翻译成 422 {@code CATEGORY_IN_USE}(领域文档 §3.4)。
 */
@Getter
@Setter
@TableName("category")
public class Category extends AuditableEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private CategoryType type;

    /** 排序值,越小越前。 */
    private Integer sortOrder;

    /** 1 启用 / 0 禁用。 */
    private EnableStatus status;
}
