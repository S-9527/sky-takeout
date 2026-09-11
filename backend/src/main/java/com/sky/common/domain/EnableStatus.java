package com.sky.common.domain;

import com.baomidou.mybatisplus.annotation.IEnum;

import com.sky.common.error.BusinessException;
import com.sky.common.error.CommonErrorCode;

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

    /**
     * 按数据库取值(1/0)还原。只接受这两个值——接口传 2 或 null 都应该报 400,
     * 而不是悄悄当成"禁用":静默纠正非法输入会让前端的问题永远暴露不出来。
     */
    public static EnableStatus of(Integer value) {
        if (value != null) {
            for (EnableStatus status : values()) {
                if (status.value == value) {
                    return status;
                }
            }
        }
        throw new BusinessException(CommonErrorCode.COMMON_VALIDATION_FAILED,
                "status 只能是 1(启用)或 0(禁用)");
    }
}
