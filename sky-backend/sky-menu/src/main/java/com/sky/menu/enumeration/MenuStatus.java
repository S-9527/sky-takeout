package com.sky.menu.enumeration;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 菜品/套餐/分类销售状态：1起售 0停售
 */
@Getter
@RequiredArgsConstructor
public enum MenuStatus {

    ON_SALE(1, "起售"),
    OFF_SALE(0, "停售");

    private final Integer code;
    private final String desc;

    public static MenuStatus fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (MenuStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }
}
