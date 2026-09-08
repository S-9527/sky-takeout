package com.sky.result;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 统一返回码：HTTP 语义分层，200 成功，其余按状态码表达业务结果
 */
@Getter
@RequiredArgsConstructor
public enum ResultCode {

    SUCCESS(200, "操作成功"),
    PARAM_ERROR(400, "参数缺失或格式错误"),
    UNAUTHORIZED(401, "未登录或令牌已失效"),
    FORBIDDEN(403, "权限不足，无法访问"),
    NOT_FOUND(404, "资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方式不支持"),
    UNPROCESSABLE_ENTITY(422, "参数取值非法"),
    CONFLICT(409, "状态冲突，不允许的操作"),
    ERROR(500, "业务或系统异常");

    private final Integer code;
    private final String msg;

    public static ResultCode fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (ResultCode resultCode : values()) {
            if (resultCode.code.equals(code)) {
                return resultCode;
            }
        }
        return null;
    }
}
