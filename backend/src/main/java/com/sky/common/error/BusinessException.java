package com.sky.common.error;

import java.util.List;

/**
 * 业务规则被违反时抛出。由 {@link GlobalExceptionHandler} 翻译为统一错误体。
 *
 * <p>只在"请求本身合法,但业务上不允许"时使用(如订单状态不允许该操作);
 * 参数格式错误交给 Bean Validation,不要抛这个。
 */
public class BusinessException extends RuntimeException {

    private final transient ErrorCode errorCode;
    private final transient List<ErrorResponse.Detail> details;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.defaultMessage(), List.of());
    }

    public BusinessException(ErrorCode errorCode, String message) {
        this(errorCode, message, List.of());
    }

    public BusinessException(ErrorCode errorCode, String message, List<ErrorResponse.Detail> details) {
        super(message);
        this.errorCode = errorCode;
        this.details = details == null ? List.of() : List.copyOf(details);
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public List<ErrorResponse.Detail> details() {
        return details;
    }

    /** 无堆栈填充:业务异常是预期内的控制流,不需要栈信息。 */
    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }
}
