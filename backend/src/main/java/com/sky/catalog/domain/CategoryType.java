package com.sky.catalog.domain;

import com.baomidou.mybatisplus.annotation.IEnum;

/**
 * 分类类型。数据库存 VARCHAR:DISH / SETMEAL。
 *
 * <p>类型决定这个分类下能挂什么:菜品只能进 {@code DISH} 分类,套餐只能进 {@code SETMEAL} 分类。
 * 这是一条跨实体的规则,由应用层在写商品时校验(领域文档 §3.4/§3.5/§3.6)。
 */
public enum CategoryType implements IEnum<String> {

    /** 菜品分类:只挂菜品。 */
    DISH,

    /** 套餐分类:只挂套餐。 */
    SETMEAL;

    @Override
    public String getValue() {
        return name();
    }
}
