package com.sky.insights.domain;

import org.springframework.http.HttpStatus;

import com.sky.common.error.ErrorCode;

/**
 * 洞察/报表错误码。取值与 {@code docs/03-api.md} 的 REPORT_* 表逐字一致。
 *
 * <p>放在 insights 而不是 order:日期区间规则属于"报表"这一关注点。订单管理端的分页也要用同一套规则,
 * 于是它调用 {@code InsightsService} 侧的服务(跨上下文只能经由对方 service 包,架构规则 L4),
 * 而不是把同一份校验抄两遍。
 */
public enum InsightsErrorCode implements ErrorCode {

    REPORT_DATE_RANGE_INVALID(HttpStatus.BAD_REQUEST, "日期区间不正确"),
    REPORT_DATE_RANGE_TOO_LARGE(HttpStatus.BAD_REQUEST, "统计区间不能超过 366 天"),
    REPORT_STATISTICS_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "统计加载失败,请稍后重试");

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    InsightsErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    @Override
    public String code() {
        return name();
    }

    @Override
    public HttpStatus httpStatus() {
        return httpStatus;
    }

    @Override
    public String defaultMessage() {
        return defaultMessage;
    }
}
