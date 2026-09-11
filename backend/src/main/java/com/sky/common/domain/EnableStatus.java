package com.sky.common.domain;

import com.baomidou.mybatisplus.annotation.IEnum;

/**
 * 共享内核:启停用状态。员工、顾客、分类、菜品、套餐都用它,且语义完全一致,所以放在这里而不是各上下文各写一遍。
 *
 * <p>数据库存 TINYINT:1 启用 / 0 禁用。
 */
public enum EnableStatus implements IEnum<Integer> {

    ENABLED(1),
    DISABLED(0);

    private final int value;

    EnableStatus(int value) {
        this.value = value;
    }

    @Override
    public Integer getValue() {
        return value;
    }

    public boolean isEnabled() {
        return this == ENABLED;
    }
}
