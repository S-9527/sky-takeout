package com.sky.insights.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.sky.common.error.BusinessException;
import com.sky.common.util.Times;
import com.sky.insights.domain.InsightsErrorCode;
import com.sky.insights.mapper.DailyOrderCount;
import com.sky.insights.mapper.DailyTurnover;
import com.sky.insights.mapper.DailyUserCount;
import com.sky.insights.mapper.DishSales;
import com.sky.insights.mapper.InsightsMapper;
import com.sky.insights.mapper.OrderStatusTotal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InsightsServiceTest {

    private final InsightsMapper insightsMapper = mock(InsightsMapper.class);

    private final InsightsService insightsService =
            new InsightsService(insightsMapper, new DateRangeValidator());

    private static DailyTurnover turnover(LocalDate day, long revenue, long orders) {
        DailyTurnover row = new DailyTurnover();
        row.setDay(day);
        row.setRevenueCents(revenue);
        row.setOrderCount(orders);
        return row;
    }

    private static DailyOrderCount orders(LocalDate day, long total, long valid) {
        DailyOrderCount row = new DailyOrderCount();
        row.setDay(day);
        row.setTotalOrderCount(total);
        row.setValidOrderCount(valid);
        return row;
    }

    private static DailyUserCount users(LocalDate day, long count) {
        DailyUserCount row = new DailyUserCount();
        row.setDay(day);
        row.setNewUserCount(count);
        return row;
    }

    private static OrderStatusTotal status(String name, long total) {
        OrderStatusTotal row = new OrderStatusTotal();
        row.setStatus(name);
        row.setTotal(total);
        return row;
    }

    // ---------------------------------------------------------------- 营业额

    @Test
    void turnoverFillsMissingDaysAndComputesAverage() {
        LocalDate begin = LocalDate.of(2025, 1, 1);
        LocalDate end = LocalDate.of(2025, 1, 3);
        when(insightsMapper.turnoverByDay(any(), any())).thenReturn(List.of(
                turnover(begin, 30000L, 3L),
                turnover(end, 10000L, 0L)));

        InsightsService.TurnoverStats stats = insightsService.turnover(begin, end);

        assertThat(stats.daily()).hasSize(3);
        assertThat(stats.daily().get(0).revenueCents()).isEqualTo(30000L);
        assertThat(stats.daily().get(0).averageOrderCents()).isEqualTo(10000L);
        // 缺席日期补 0
        assertThat(stats.daily().get(1).revenueCents()).isZero();
        assertThat(stats.daily().get(1).averageOrderCents()).isZero();
        assertThat(stats.sum()).isEqualTo(40000L);
        assertThat(stats.beginDate()).isEqualTo(begin);
        assertThat(stats.endDate()).isEqualTo(end);
    }

    @Test
    void turnoverDefaultsToLastSevenDays() {
        when(insightsMapper.turnoverByDay(any(), any())).thenReturn(List.of());

        InsightsService.TurnoverStats stats = insightsService.turnover(null, null);

        LocalDate today = Times.nowLocal().toLocalDate();
        assertThat(stats.endDate()).isEqualTo(today);
        assertThat(stats.beginDate()).isEqualTo(today.minusDays(6));
        assertThat(stats.daily()).hasSize(7);
        assertThat(stats.sum()).isZero();
    }

    @Test
    void turnoverPropagatesDateRangeViolation() {
        assertThatThrownBy(() -> insightsService.turnover(LocalDate.of(2025, 1, 2), LocalDate.of(2025, 1, 1)))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(InsightsErrorCode.REPORT_DATE_RANGE_INVALID));
    }

    /** 报表查询失败 → 500 REPORT_STATISTICS_FAILED(前端能给出"统计加载失败"的专门提示)。 */
    @Test
    void queryFailureIsTranslatedToReportStatisticsFailed() {
        when(insightsMapper.turnoverByDay(any(), any())).thenThrow(new IllegalStateException("db down"));

        assertThatThrownBy(() -> insightsService.turnover(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 7)))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(InsightsErrorCode.REPORT_STATISTICS_FAILED));
    }

    // ---------------------------------------------------------------- 用户

    @Test
    void userStatsCountsNewUsersAndRunningTotals() {
        LocalDate begin = LocalDate.of(2025, 1, 1);
        LocalDate end = LocalDate.of(2025, 1, 3);
        LocalDate day2 = LocalDate.of(2025, 1, 2);
        when(insightsMapper.usersByDay(any(), any())).thenReturn(List.of(
                users(begin, 2L), users(day2, 3L)));
        when(insightsMapper.countUsersUntil(any())).thenReturn(10L);

        InsightsService.UserStats stats = insightsService.userStats(begin, end);

        assertThat(stats.newUserCount()).isEqualTo(5L);
        assertThat(stats.totalUserCount()).isEqualTo(10L);
        assertThat(stats.daily()).hasSize(3);
        // "截至某日的总数"从区间末总数往前倒推:1/3=10、1/2=10-0=10、1/1=10-0-3=7
        assertThat(stats.daily().get(2).totalUserCount()).isEqualTo(10L);
        assertThat(stats.daily().get(1).totalUserCount()).isEqualTo(10L);
        assertThat(stats.daily().get(0).totalUserCount()).isEqualTo(7L);
        assertThat(stats.daily().get(1).newUserCount()).isEqualTo(3L);
        assertThat(stats.daily().get(2).newUserCount()).isZero();
    }

    // ---------------------------------------------------------------- 订单

    @Test
    void orderStatsComputesValidRateAndZeroWhenEmpty() {
        LocalDate begin = LocalDate.of(2025, 1, 1);
        LocalDate end = LocalDate.of(2025, 1, 2);
        when(insightsMapper.ordersByDay(any(), any())).thenReturn(List.of(orders(begin, 4L, 3L)));

        InsightsService.OrderStats stats = insightsService.orderStats(begin, end);

        assertThat(stats.totalOrderCount()).isEqualTo(4L);
        assertThat(stats.validOrderCount()).isEqualTo(3L);
        assertThat(stats.validOrderRate()).isEqualTo(0.75d);
        assertThat(stats.daily().get(1).totalOrderCount()).isZero();

        when(insightsMapper.ordersByDay(any(), any())).thenReturn(List.of());
        assertThat(insightsService.orderStats(begin, end).validOrderRate()).isZero();
    }

    // ---------------------------------------------------------------- 销量排行

    @Test
    void topDishesRanksAndClampsTopNumber() {
        DishSales first = new DishSales();
        first.setName("水煮牛肉");
        first.setCopies(86L);
        DishSales second = new DishSales();
        second.setName("宫保鸡丁");
        second.setCopies(64L);
        when(insightsMapper.topDishes(any(), any(), anyInt())).thenReturn(List.of(first, second));

        InsightsService.TopDishSales defaulted = insightsService.topDishes(null, null, null);
        assertThat(defaulted.topNumber()).isEqualTo(InsightsService.DEFAULT_TOP_NUMBER);
        assertThat(defaulted.items()).extracting(InsightsService.TopDish::rank).containsExactly(1, 2);
        assertThat(defaulted.items().get(0).name()).isEqualTo("水煮牛肉");

        // 上限 20:超限钳制而不是报错(契约 §2.4 第 8 项)
        assertThat(insightsService.topDishes(null, null, 999).topNumber()).isEqualTo(20);
        assertThat(insightsService.topDishes(null, null, 5).topNumber()).isEqualTo(5);
        assertThat(insightsService.topDishes(null, null, 0).topNumber()).isEqualTo(10);
    }

    // ---------------------------------------------------------------- 工作台

    @Test
    void workbenchAggregatesTodayAndOverviewBadges() {
        LocalDate today = Times.nowLocal().toLocalDate();
        when(insightsMapper.turnoverByDay(any(), any())).thenReturn(List.of(turnover(today, 186000L, 18L)));
        when(insightsMapper.countOrdersPlacedBetween(any(), any())).thenReturn(21L);
        when(insightsMapper.usersByDay(any(), any())).thenReturn(List.of(users(today, 6L)));
        when(insightsMapper.orderStatusTotals()).thenReturn(List.of(
                status("PENDING_PAYMENT", 3L), status("PENDING_ACCEPTANCE", 5L), status("ACCEPTED", 2L),
                status("DELIVERING", 1L), status("COMPLETED", 120L), status("CANCELLED", 6L)));
        when(insightsMapper.countDishesByStatus(1)).thenReturn(15L);
        when(insightsMapper.countDishesByStatus(0)).thenReturn(6L);
        when(insightsMapper.countSetmealsByStatus(1)).thenReturn(4L);
        when(insightsMapper.countSetmealsByStatus(0)).thenReturn(1L);
        when(insightsMapper.countZeroSalesOnSaleDishes(any(), any())).thenReturn(2L);

        InsightsService.Workbench workbench = insightsService.workbench();

        assertThat(workbench.today().turnoverCents()).isEqualTo(186000L);
        assertThat(workbench.today().validOrderCount()).isEqualTo(18L);
        assertThat(workbench.today().totalOrderCount()).isEqualTo(21L);
        assertThat(workbench.today().newUserCount()).isEqualTo(6L);
        assertThat(workbench.today().pendingAcceptanceCount()).isEqualTo(5L);
        assertThat(workbench.today().pendingDeliveryCount()).isEqualTo(2L);

        assertThat(workbench.orderOverview()).extracting(InsightsService.OverviewItem::name)
                .containsExactly("allOrders", "validOrders", "cancelledOrders", "pendingPayment",
                        "pendingAcceptance", "accepted", "delivering", "completed");
        assertThat(workbench.orderOverview().get(0).value()).isEqualTo(137L);
        assertThat(workbench.orderOverview().get(1).title()).isEqualTo("有效订单");
        assertThat(workbench.dishOverview()).extracting(InsightsService.OverviewItem::name)
                .containsExactly("onSaleDishes", "offSaleDishes", "onSaleSetmeals", "offSaleSetmeals", "soldOutDishes");
        assertThat(workbench.dishOverview().get(4).value()).isEqualTo(2L);
    }

    @Test
    void workbenchToleratesEmptyDay() {
        when(insightsMapper.turnoverByDay(any(), any())).thenReturn(List.of());
        when(insightsMapper.usersByDay(any(), any())).thenReturn(List.of());
        when(insightsMapper.orderStatusTotals()).thenReturn(List.of());

        InsightsService.Workbench workbench = insightsService.workbench();

        assertThat(workbench.today().turnoverCents()).isZero();
        assertThat(workbench.today().newUserCount()).isZero();
        assertThat(workbench.orderOverview().get(0).value()).isZero();
    }

    @Test
    void workbenchFailureIsReportedAsStatisticsFailed() {
        when(insightsMapper.orderStatusTotals()).thenThrow(new IllegalStateException("db down"));

        assertThatThrownBy(() -> insightsService.workbench())
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(InsightsErrorCode.REPORT_STATISTICS_FAILED));
    }

    @Test
    void internalRangeUsesHalfOpenBoundsWithInclusiveDates() {
        LocalDate begin = LocalDate.of(2025, 1, 1);
        LocalDate end = LocalDate.of(2025, 1, 2);
        when(insightsMapper.turnoverByDay(any(), any())).thenAnswer(invocation -> {
            LocalDateTime from = invocation.getArgument(0);
            LocalDateTime to = invocation.getArgument(1);
            assertThat(from).isEqualTo(begin.atStartOfDay());
            // 含首含尾:结束日要走到次日 0 点
            assertThat(to).isEqualTo(end.plusDays(1).atStartOfDay());
            return List.of();
        });

        insightsService.turnover(begin, end);
    }
}
