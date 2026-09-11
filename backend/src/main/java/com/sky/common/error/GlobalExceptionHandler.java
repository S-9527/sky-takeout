package com.sky.common.error;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

import com.sky.common.web.TraceIdFilter;

/**
 * 把各种异常翻译成统一错误体 {@code {code, message, details[], traceId}}。
 *
 * <p>原则:
 * <ul>
 *   <li>HTTP 状态码必须是真实语义,不能一律 200</li>
 *   <li>"能理解但越界"的参数 → 400 校验失败;可预期的业务拒绝 → 由 {@link BusinessException} 自带的错误码决定</li>
 *   <li>预期内的业务异常只记 warn 且不打堆栈;未预期异常打完整堆栈,但对外只说通用提示</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex) {
        log.warn("业务异常 code={} message={}", ex.errorCode().code(), ex.getMessage());
        return build(ex.errorCode(), ex.getMessage(), ex.details());
    }

    /** {@code @RequestBody} 上的 Bean Validation 失败。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleBodyValidation(MethodArgumentNotValidException ex) {
        List<ErrorResponse.Detail> details = ex.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::toDetail)
                .toList();
        return build(CommonErrorCode.COMMON_VALIDATION_FAILED, details);
    }

    /** 方法参数上的约束校验失败({@code @Validated} + {@code @RequestParam} 等)。 */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleMethodValidation(HandlerMethodValidationException ex) {
        List<ErrorResponse.Detail> details = ex.getAllErrors().stream()
                .map(error -> new ErrorResponse.Detail(
                        error instanceof FieldError fieldError ? fieldError.getField() : null,
                        error.getDefaultMessage()))
                .toList();
        return build(CommonErrorCode.COMMON_VALIDATION_FAILED, details);
    }

    /** Service 层主动校验参数时抛出的约束违反。 */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        List<ErrorResponse.Detail> details = ex.getConstraintViolations().stream()
                .map(v -> new ErrorResponse.Detail(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        return build(CommonErrorCode.COMMON_VALIDATION_FAILED, details);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex) {
        log.warn("请求体不可读: {}", ex.getMessage());
        return build(CommonErrorCode.COMMON_VALIDATION_FAILED, List.of());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException ex) {
        return build(CommonErrorCode.COMMON_VALIDATION_FAILED,
                List.of(new ErrorResponse.Detail(ex.getParameterName(), "该参数必填")));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return build(CommonErrorCode.COMMON_VALIDATION_FAILED,
                List.of(new ErrorResponse.Detail(ex.getName(), "参数类型不正确")));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return build(CommonErrorCode.COMMON_METHOD_NOT_ALLOWED, List.of());
    }

    /** 静态资源与未匹配路由。 */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException ex) {
        return build(CommonErrorCode.COMMON_RESOURCE_NOT_FOUND, List.of());
    }

    /** 唯一键冲突:数据库兜底约束被触发,翻译成 409 而不是 500。 */
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateKey(DuplicateKeyException ex) {
        log.warn("唯一键冲突: {}", ex.getMostSpecificCause().getMessage());
        return build(CommonErrorCode.COMMON_CONFLICT, List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("未预期异常", ex);
        return build(CommonErrorCode.COMMON_INTERNAL_ERROR, List.of());
    }

    private static ErrorResponse.Detail toDetail(FieldError fieldError) {
        return new ErrorResponse.Detail(fieldError.getField(), fieldError.getDefaultMessage());
    }

    private static ResponseEntity<ErrorResponse> build(ErrorCode errorCode, List<ErrorResponse.Detail> details) {
        return build(errorCode, errorCode.defaultMessage(), details);
    }

    private static ResponseEntity<ErrorResponse> build(ErrorCode errorCode, String message,
                                                       List<ErrorResponse.Detail> details) {
        ErrorResponse body = new ErrorResponse(
                errorCode.code(),
                message,
                details == null ? List.of() : details,
                TraceIdFilter.currentTraceId());
        return ResponseEntity.status(errorCode.httpStatus()).body(body);
    }
}
