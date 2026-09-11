package com.sky.insights.api;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import com.sky.insights.api.dto.OrderStatsResponse;
import com.sky.insights.api.dto.TopDishSalesResponse;
import com.sky.insights.api.dto.TurnoverStatsResponse;
import com.sky.insights.api.dto.UserStatsResponse;
import com.sky.insights.api.dto.WorkbenchResponse;
import com.sky.insights.service.InsightsService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InsightsControllerTest {

    private final InsightsService insightsService = mock(InsightsService.class);
    private final InsightsController controller = new InsightsController(insightsService);

    private static final LocalDate BEGIN = LocalDate.of(2025, 1, 1);
    private static final LocalDate END = LocalDate.of(2025, 1, 7);

    @Test
    void turnoverMapsDailyItems() {
        when(insightsService.turnover(BEGIN, END)).thenReturn(new InsightsService.TurnoverStats(
                BEGIN, END, 40000L,
                List.of(new InsightsService.TurnoverDay(BEGIN, 30000L, 3L, 10000L))));

        TurnoverStatsResponse response = controller.turnover(BEGIN, END);

        assertThat(response.sum()).isEqualTo(40000L);
        assertThat(response.daily()).hasSize(1);
        assertThat(response.daily().get(0).averageOrderCents()).isEqualTo(10000L);
        verify(insightsService).turnover(BEGIN, END);
    }

    @Test
    void userStatsMapsTotals() {
        when(insightsService.userStats(null, null)).thenReturn(new InsightsService.UserStats(
                BEGIN, END, 5L, 830L, List.of(new InsightsService.UserDay(BEGIN, 2L, 825L))));

        UserStatsResponse response = controller.userStats(null, null);

        assertThat(response.newUserCount()).isEqualTo(5L);
        assertThat(response.totalUserCount()).isEqualTo(830L);
        assertThat(response.daily().get(0).totalUserCount()).isEqualTo(825L);
    }

    @Test
    void orderStatsMapsRate() {
        when(insightsService.orderStats(BEGIN, END)).thenReturn(new InsightsService.OrderStats(
                BEGIN, END, 137L, 120L, 0.8759d,
                List.of(new InsightsService.OrderDay(BEGIN, 20L, 18L))));

        OrderStatsResponse response = controller.orderStats(BEGIN, END);

        assertThat(response.totalOrderCount()).isEqualTo(137L);
        assertThat(response.validOrderRate()).isEqualTo(0.8759d);
        assertThat(response.daily().get(0).validOrderCount()).isEqualTo(18L);
    }

    @Test
    void topDishesPassesTopNumberThrough() {
        when(insightsService.topDishes(BEGIN, END, 5)).thenReturn(new InsightsService.TopDishSales(
                5, List.of(new InsightsService.TopDish(1, "水煮牛肉", 86L))));

        TopDishSalesResponse response = controller.topDishes(BEGIN, END, 5);

        assertThat(response.topNumber()).isEqualTo(5);
        assertThat(response.items().get(0).rank()).isEqualTo(1);
        assertThat(response.items().get(0).name()).isEqualTo("水煮牛肉");
        verify(insightsService).topDishes(BEGIN, END, 5);
    }

    @Test
    void workbenchMapsTodayAndOverview() {
        when(insightsService.workbench()).thenReturn(new InsightsService.Workbench(
                new InsightsService.WorkbenchToday(186000L, 18L, 21L, 6L, 5L, 2L),
                List.of(new InsightsService.OverviewItem("allOrders", "全部订单", 137L)),
                List.of(new InsightsService.OverviewItem("soldOutDishes", "今日零销量", 2L))));

        WorkbenchResponse response = controller.workbench();

        assertThat(response.today().turnoverCents()).isEqualTo(186000L);
        assertThat(response.today().pendingAcceptanceCount()).isEqualTo(5L);
        assertThat(response.today().pendingDeliveryCount()).isEqualTo(2L);
        assertThat(response.orderOverview().get(0).name()).isEqualTo("allOrders");
        assertThat(response.dishOverview().get(0).title()).isEqualTo("今日零销量");
    }
}
