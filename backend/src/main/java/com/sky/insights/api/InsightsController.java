package com.sky.insights.api;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

import com.sky.insights.api.dto.OrderStatsResponse;
import com.sky.insights.api.dto.TopDishSalesResponse;
import com.sky.insights.api.dto.TurnoverStatsResponse;
import com.sky.insights.api.dto.UserStatsResponse;
import com.sky.insights.api.dto.WorkbenchResponse;
import com.sky.insights.service.InsightsService;

/**
 * 管理端数据洞察。前缀由 SecurityConfig 限制为 ADMIN / STAFF。
 *
 * <p>日期区间含首含尾;都不传时默认最近 7 天。规则与错误码由
 * {@code DateRangeValidator} 统一给出(订单管理端分页复用同一套)。
 */
@RestController
@RequestMapping("/api/v1/admin/insights")
public class InsightsController {

    private final InsightsService insightsService;

    public InsightsController(InsightsService insightsService) {
        this.insightsService = insightsService;
    }

    @GetMapping("/turnover-stats")
    public TurnoverStatsResponse turnover(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate beginDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return TurnoverStatsResponse.from(insightsService.turnover(beginDate, endDate));
    }

    @GetMapping("/user-stats")
    public UserStatsResponse userStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate beginDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return UserStatsResponse.from(insightsService.userStats(beginDate, endDate));
    }

    @GetMapping("/order-stats")
    public OrderStatsResponse orderStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate beginDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return OrderStatsResponse.from(insightsService.orderStats(beginDate, endDate));
    }

    @GetMapping("/top-dishes")
    public TopDishSalesResponse topDishes(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate beginDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Integer topNumber) {
        return TopDishSalesResponse.from(insightsService.topDishes(beginDate, endDate, topNumber));
    }

    @GetMapping("/workbench")
    public WorkbenchResponse workbench() {
        return WorkbenchResponse.from(insightsService.workbench());
    }
}
