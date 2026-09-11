package com.sky.common.error;

import java.util.List;

/**
 * 统一的错误响应体。
 *
 * <p>成功响应**不使用**这个信封:成功时直接返回资源本身或数组。
 * 只有失败才用这个结构,形状固定为 {@code {code, message, details[], traceId}}。
 */
public record ErrorResponse(
        String code,
        String message,
        List<Detail> details,
        String traceId
) {

    /** 字段级失败原因。没有字段级信息时为空数组,而不是 null。 */
    public record Detail(String field, String reason) {
    }
}
