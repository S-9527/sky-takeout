package com.sky.insights.service;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import com.sky.common.error.BusinessException;
import com.sky.common.error.ErrorResponse;
import com.sky.insights.domain.InsightsErrorCode;

/**
 * 日期区间校验(契约 §2.4 第 8 / 9 项):含首含尾;上限 366 天;起止必须同时给或同时不给。
 *
 * <p>订单管理端的分页与报表接口共用这一段规则,所以它属于洞察上下文并作为 service 暴露
 * (跨上下文调用只能经由 service 包)。
 */
@Service
public class DateRangeValidator {

    /** 区间跨度上限(含首含尾,366 天覆盖闰年)。 */
    public static final long MAX_RANGE_DAYS = 366;

    /**
     * 校验可选区间。
     *
     * @throws BusinessException 只给了一半(400 {@code REPORT_DATE_RANGE_INVALID})
     *                           或起 > 止(400 {@code REPORT_DATE_RANGE_INVALID})
     *                           或跨度超限(400 {@code REPORT_DATE_RANGE_TOO_LARGE})
     */
    public void validate(LocalDate begin, LocalDate end) {
        if (begin == null && end == null) {
            return;
        }
        if (begin == null || end == null) {
            throw new BusinessException(InsightsErrorCode.REPORT_DATE_RANGE_INVALID,
                    "日期区间不正确",
                    java.util.List.of(new ErrorResponse.Detail("beginDate", "起止日期必须同时提供")));
        }
        if (begin.isAfter(end)) {
            throw new BusinessException(InsightsErrorCode.REPORT_DATE_RANGE_INVALID,
                    "日期区间不正确",
                    java.util.List.of(new ErrorResponse.Detail("endDate", "结束日期不能早于开始日期")));
        }
        // 含首含尾,所以天数要 +1
        long days = ChronoUnit.DAYS.between(begin, end) + 1;
        if (days > MAX_RANGE_DAYS) {
            throw new BusinessException(InsightsErrorCode.REPORT_DATE_RANGE_TOO_LARGE);
        }
    }
}
