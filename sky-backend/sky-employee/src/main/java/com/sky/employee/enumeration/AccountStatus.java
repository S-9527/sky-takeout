package com.sky.employee.enumeration;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 账号状态：1启用 0禁用
 */
@Getter
@RequiredArgsConstructor
public enum AccountStatus {

    ENABLED(1, "启用"),
    DISABLED(0, "禁用");

    private final Integer code;
    private final String desc;

    public static AccountStatus fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (AccountStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }
}
