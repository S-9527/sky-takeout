package com.sky.common.error;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.Set;

import com.sky.common.web.TraceIdFilter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 统一错误体的翻译测试:每种异常必须落到契约规定的状态码与错误码上,且都带 traceId。
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    private static MethodParameter anyMethodParameter() throws NoSuchMethodException {
        return new MethodParameter(String.class.getDeclaredMethod("substring", int.class), -1);
    }

    private static ErrorResponse body(ResponseEntity<ErrorResponse> response) {
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }

    private static void assertError(ResponseEntity<ErrorResponse> response, HttpStatus status, String code) {
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(body(response).code()).isEqualTo(code);
        assertThat(body(response).traceId()).isEqualTo("trace-123");
    }

    @Test
    void businessExceptionKeepsItsOwnStatusAndDetails() {
        MDC.put(TraceIdFilter.MDC_KEY, "trace-123");
        BusinessException ex = new BusinessException(TestErrorCode.TEAPOT,
                "自定义提示", List.of(new ErrorResponse.Detail("field", "原因")));

        ResponseEntity<ErrorResponse> response = handler.handleBusiness(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.I_AM_A_TEAPOT);
        assertThat(body(response).message()).isEqualTo("自定义提示");
        assertThat(body(response).details()).containsExactly(new ErrorResponse.Detail("field", "原因"));
        assertThat(body(response).traceId()).isEqualTo("trace-123");
    }

    @Test
    void businessExceptionWithoutMessageUsesDefaultMessage() {
        MDC.put(TraceIdFilter.MDC_KEY, "trace-123");

        ResponseEntity<ErrorResponse> response = handler.handleBusiness(
                new BusinessException(CommonErrorCode.COMMON_CONFLICT));

        assertError(response, HttpStatus.CONFLICT, "COMMON_CONFLICT");
        assertThat(body(response).message()).isEqualTo(CommonErrorCode.COMMON_CONFLICT.defaultMessage());
    }

    @Test
    void bodyValidationFailureBecomesCommonValidationFailedWithFieldDetails() throws Exception {
        MDC.put(TraceIdFilter.MDC_KEY, "trace-123");
        BindingResult bindingResult = mock(BindingResult.class);
        when(bindingResult.getFieldErrors())
                .thenReturn(List.of(new FieldError("request", "name", "姓名不能为空")));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(anyMethodParameter(), bindingResult);

        ResponseEntity<ErrorResponse> response = handler.handleBodyValidation(ex);

        assertError(response, HttpStatus.BAD_REQUEST, "COMMON_VALIDATION_FAILED");
        assertThat(body(response).details()).containsExactly(new ErrorResponse.Detail("name", "姓名不能为空"));
    }

    @Test
    void constraintViolationBecomesCommonValidationFailed() {
        MDC.put(TraceIdFilter.MDC_KEY, "trace-123");
        Path path = mock(Path.class);
        when(path.toString()).thenReturn("pageSize");
        ConstraintViolation<?> violation = mock(ConstraintViolation.class);
        when(violation.getPropertyPath()).thenReturn(path);
        when(violation.getMessage()).thenReturn("必须为正整数");

        ResponseEntity<ErrorResponse> response = handler.handleConstraintViolation(
                new ConstraintViolationException(Set.of(violation)));

        assertError(response, HttpStatus.BAD_REQUEST, "COMMON_VALIDATION_FAILED");
        assertThat(body(response).details()).containsExactly(new ErrorResponse.Detail("pageSize", "必须为正整数"));
    }

    @Test
    void unreadableBodyBecomesCommonValidationFailed() {
        MDC.put(TraceIdFilter.MDC_KEY, "trace-123");

        ResponseEntity<ErrorResponse> response = handler.handleUnreadable(
                new HttpMessageNotReadableException("bad json", (org.springframework.http.HttpInputMessage) null));

        assertError(response, HttpStatus.BAD_REQUEST, "COMMON_VALIDATION_FAILED");
    }

    @Test
    void missingRequestParameterIsReportedWithFieldName() {
        MDC.put(TraceIdFilter.MDC_KEY, "trace-123");

        ResponseEntity<ErrorResponse> response = handler.handleMissingParam(
                new MissingServletRequestParameterException("page", "Integer"));

        assertError(response, HttpStatus.BAD_REQUEST, "COMMON_VALIDATION_FAILED");
        assertThat(body(response).details()).containsExactly(new ErrorResponse.Detail("page", "该参数必填"));
    }

    @Test
    void typeMismatchIsReportedWithFieldName() throws Exception {
        MDC.put(TraceIdFilter.MDC_KEY, "trace-123");
        MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException(
                "abc", Integer.class, "page", anyMethodParameter(), new IllegalArgumentException());

        ResponseEntity<ErrorResponse> response = handler.handleTypeMismatch(ex);

        assertError(response, HttpStatus.BAD_REQUEST, "COMMON_VALIDATION_FAILED");
        assertThat(body(response).details()).containsExactly(new ErrorResponse.Detail("page", "参数类型不正确"));
    }

    @Test
    void wrongHttpMethodIsNotSilentlyTreatedAs404() {
        MDC.put(TraceIdFilter.MDC_KEY, "trace-123");

        ResponseEntity<ErrorResponse> response = handler.handleMethodNotSupported(
                new HttpRequestMethodNotSupportedException("PATCH"));

        assertError(response, HttpStatus.METHOD_NOT_ALLOWED, "COMMON_METHOD_NOT_ALLOWED");
    }

    @Test
    void unmatchedRouteBecomes404() {
        MDC.put(TraceIdFilter.MDC_KEY, "trace-123");

        ResponseEntity<ErrorResponse> response = handler.handleNoResource(
                new NoResourceFoundException(HttpMethod.GET, "/api/v1/nope", "nope"));

        assertError(response, HttpStatus.NOT_FOUND, "COMMON_RESOURCE_NOT_FOUND");
    }

    /** 唯一键冲突是并发下的正常结果,必须翻译成 409 而不是 500。 */
    @Test
    void duplicateKeyBecomes409() {
        MDC.put(TraceIdFilter.MDC_KEY, "trace-123");

        ResponseEntity<ErrorResponse> response = handler.handleDuplicateKey(
                new DuplicateKeyException("uk_employee_username"));

        assertError(response, HttpStatus.CONFLICT, "COMMON_CONFLICT");
    }

    /** 未预期异常对外只说通用提示,内部细节不外泄。 */
    @Test
    void unexpectedExceptionBecomes500WithoutLeakingInternals() {
        MDC.put(TraceIdFilter.MDC_KEY, "trace-123");

        ResponseEntity<ErrorResponse> response = handler.handleUnexpected(
                new IllegalStateException("数据库连接串是 jdbc:mysql://secret"));

        assertError(response, HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_INTERNAL_ERROR");
        assertThat(body(response).message()).doesNotContain("secret");
        assertThat(body(response).details()).isEmpty();
    }

    /** 只在本测试里使用的错误码,避免为一个测试去污染生产错误码表。 */
    private enum TestErrorCode implements ErrorCode {
        TEAPOT;

        @Override
        public String code() {
            return name();
        }

        @Override
        public HttpStatus httpStatus() {
            return HttpStatus.I_AM_A_TEAPOT;
        }

        @Override
        public String defaultMessage() {
            return "我是茶壶";
        }
    }
}
