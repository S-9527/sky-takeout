package com.sky.insights.service;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.sky.common.error.BusinessException;
import com.sky.common.util.Times;
import com.sky.insights.domain.InsightsErrorCode;
import com.sky.insights.mapper.DailyOrderCount;
import com.sky.insights.mapper.DailyTurnover;
import com.sky.insights.mapper.DailyUserCount;
import com.sky.insights.mapper.DishSales;
import com.sky.insights.mapper.InsightsMapper;
import com.sky.insights.mapper.OrderStatusTotal;

/**
 * 数据洞察:营业额 / 用户 / 订单 / 销量排行 / 工作台。
 *
 * <p>全部是**订单与顾客的只读投影**,不建报表表(D8):单一真相,代价是聚合查询稍重
 * (02-database §5 为此留了 {@code idx_orders_paid_at} 与 {@code idx_order_item_dish})。
 *
 * <p>口径统一:
 * <ul>
 *   <li>时间区间**含首含尾**,内部转成 {@code [from, to)} 半开区间;</li>
 *   <li>营业额与订单量按 {@code paid_at} 聚合,有效订单 = {@code COMPLETED};</li>
 *   <li>"今日"按门店时区(Asia/Shanghai)的自然日。</li>
 * </ul>
 */
@Service
public class InsightsService {

    /** 未传日期区间时的默认跨度:最近 7 天(契约 §2.4 第 9 项)。 */
    public static final int DEFAULT_RANGE_DAYS = 7;

    /** 销量排行默认与上限(契约 §2.4 第 8 项)。 */
    public static final int DEFAULT_TOP_NUMBER = 10;
    public static final int MAX_TOP_NUMBER = 20;

    private final InsightsMapper insightsMapper;
    private final DateRangeValidator dateRangeValidator;

    public InsightsService(InsightsMapper insightsMapper, DateRangeValidator dateRangeValidator) {
        this.insightsMapper = insightsMapper;
        this.dateRangeValidator = dateRangeValidator;
    }

    // ---------------------------------------------------------------- 服务层返回结构

    public record TurnoverDay(LocalDate date, long revenueCents, long orderCount, long averageOrderCents) {
    }

    public record TurnoverStats(LocalDate beginDate, LocalDate endDate, long sum, List<TurnoverDay> daily) {
    }

    public record UserDay(LocalDate date, long newUserCount, long totalUserCount) {
    }

    public record UserStats(LocalDate beginDate, LocalDate endDate, long newUserCount, long totalUserCount,
                            List<UserDay> daily) {
    }

    public record OrderDay(LocalDate date, long totalOrderCount, long validOrderCount) {
    }

    public record OrderStats(LocalDate beginDate, LocalDate endDate, long totalOrderCount, long validOrderCount,
                             double validOrderRate, List<OrderDay> daily) {
    }

    public record TopDish(int rank, String name, long copies) {
    }

    public record TopDishSales(int topNumber, List<TopDish> items) {
    }

    public record OverviewItem(String name, String title, long value) {
    }

    public record WorkbenchToday(long turnoverCents, long validOrderCount, long totalOrderCount, long newUserCount,
                                 long pendingAcceptanceCount, long pendingDeliveryCount) {
    }

    public record Workbench(WorkbenchToday today, List<OverviewItem> orderOverview,
                            List<OverviewItem> dishOverview) {
    }

    // ---------------------------------------------------------------- 报表

    public TurnoverStats turnover(LocalDate beginDate, LocalDate endDate) {
        return reporting(() -> {
            Range range = resolveRange(beginDate, endDate);
            Map<LocalDate, DailyTurnover> byDay = indexById(insightsMapper.turnoverByDay(range.from(), range.to()),
                    DailyTurnover::getDay);
            long sum = 0L;
            List<TurnoverDay> daily = new ArrayList<>();
            for (LocalDate date : range.days()) {
                DailyTurnover row = byDay.get(date);
                long revenue = row == null ? 0L : row.getRevenueCents();
                long orders = row == null ? 0L : row.getOrderCount();
                // 客单价 = 当日营业额 / 当日完成订单数;无订单时为 0
                daily.add(new TurnoverDay(date, revenue, orders, orders == 0 ? 0L : revenue / orders));
                sum += revenue;
            }
            return new TurnoverStats(range.beginDate(), range.endDate(), sum, daily);
        });
    }

    public UserStats userStats(LocalDate beginDate, LocalDate endDate) {
        return reporting(() -> {
            Range range = resolveRange(beginDate, endDate);
            Map<LocalDate, DailyUserCount> byDay = indexById(insightsMapper.usersByDay(range.from(), range.to()),
                    DailyUserCount::getDay);
            long totalAtEnd = insightsMapper.countUsersUntil(range.to());
            long newUsers = byDay.values().stream().mapToLong(DailyUserCount::getNewUserCount).sum();

            // 逐日"累计总数" = 截至该日 23:59:59 的总数,由区间末总数往前倒推
            List<UserDay> daily = new ArrayList<>();
            long running = totalAtEnd;
            for (int i = range.days().size() - 1; i >= 0; i--) {
                LocalDate date = range.days().get(i);
                DailyUserCount row = byDay.get(date);
                long newToday = row == null ? 0L : row.getNewUserCount();
                daily.add(0, new UserDay(date, newToday, running));
                running -= newToday;
            }
            return new UserStats(range.beginDate(), range.endDate(), newUsers, totalAtEnd, daily);
        });
    }

    public OrderStats orderStats(LocalDate beginDate, LocalDate endDate) {
        return reporting(() -> {
            Range range = resolveRange(beginDate, endDate);
            Map<LocalDate, DailyOrderCount> byDay = indexById(insightsMapper.ordersByDay(range.from(), range.to()),
                    DailyOrderCount::getDay);
            long total = 0L;
            long valid = 0L;
            List<OrderDay> daily = new ArrayList<>();
            for (LocalDate date : range.days()) {
                DailyOrderCount row = byDay.get(date);
                long dayTotal = row == null ? 0L : row.getTotalOrderCount();
                long dayValid = row == null ? 0L : row.getValidOrderCount();
                daily.add(new OrderDay(date, dayTotal, dayValid));
                total += dayTotal;
                valid += dayValid;
            }
            double rate = total == 0 ? 0d : (double) valid / total;
            return new OrderStats(range.beginDate(), range.endDate(), total, valid, rate, daily);
        });
    }

    public TopDishSales topDishes(LocalDate beginDate, LocalDate endDate, Integer topNumber) {
        return reporting(() -> {
            Range range = resolveRange(beginDate, endDate);
            int limit = clampTopNumber(topNumber);
            List<DishSales> rows = insightsMapper.topDishes(range.from(), range.to(), limit);
            List<TopDish> items = new ArrayList<>();
            int rank = 1;
            for (DishSales row : rows) {
                items.add(new TopDish(rank++, row.getName(), row.getCopies()));
            }
            return new TopDishSales(limit, items);
        });
    }

    /** 工作台:今日数据 + 当前待办 + 订单/菜品概览角标。 */
    public Workbench workbench() {
        return reporting(() -> {
            LocalDate today = Times.nowLocal().toLocalDate();
            LocalDateTime todayStart = today.atStartOfDay();
            LocalDateTime todayEnd = today.plusDays(1).atStartOfDay();

            Map<LocalDate, DailyTurnover> todayTurnover =
                    indexById(insightsMapper.turnoverByDay(todayStart, todayEnd), DailyTurnover::getDay);
            DailyTurnover turnover = todayTurnover.get(today);
            long turnoverCents = turnover == null ? 0L : turnover.getRevenueCents();
            long validOrderCount = turnover == null ? 0L : turnover.getOrderCount();
            long totalOrderCount = insightsMapper.countOrdersPlacedBetween(todayStart, todayEnd);
            long newUserCount = indexById(insightsMapper.usersByDay(todayStart, todayEnd), DailyUserCount::getDay)
                    .getOrDefault(today, emptyUserCount()).getNewUserCount();

            Map<String, Long> statusTotals = new LinkedHashMap<>();
            for (OrderStatusTotal row : insightsMapper.orderStatusTotals()) {
                statusTotals.put(row.getStatus(), row.getTotal());
            }
            long completed = statusTotals.getOrDefault("COMPLETED", 0L);

            WorkbenchToday todayBlock = new WorkbenchToday(
                    turnoverCents, validOrderCount, totalOrderCount, newUserCount,
                    statusTotals.getOrDefault("PENDING_ACCEPTANCE", 0L),
                    statusTotals.getOrDefault("ACCEPTED", 0L));

            List<OverviewItem> orderOverview = List.of(
                    overview("allOrders", "全部订单", statusTotals.values().stream().mapToLong(Long::longValue).sum()),
                    overview("validOrders", "有效订单", completed),
                    overview("cancelledOrders", "已取消", statusTotals.getOrDefault("CANCELLED", 0L)),
                    overview("pendingPayment", "待付款", statusTotals.getOrDefault("PENDING_PAYMENT", 0L)),
                    overview("pendingAcceptance", "待接单", statusTotals.getOrDefault("PENDING_ACCEPTANCE", 0L)),
                    overview("accepted", "待派送", statusTotals.getOrDefault("ACCEPTED", 0L)),
                    overview("delivering", "派送中", statusTotals.getOrDefault("DELIVERING", 0L)),
                    overview("completed", "已完成", completed));

            List<OverviewItem> dishOverview = List.of(
                    overview("onSaleDishes", "在售菜品", insightsMapper.countDishesByStatus(1)),
                    overview("offSaleDishes", "停售菜品", insightsMapper.countDishesByStatus(0)),
                    overview("onSaleSetmeals", "在售套餐", insightsMapper.countSetmealsByStatus(1)),
                    overview("offSaleSetmeals", "停售套餐", insightsMapper.countSetmealsByStatus(0)),
                    overview("soldOutDishes", "今日零销量", insightsMapper.countZeroSalesOnSaleDishes(todayStart, todayEnd)));

            return new Workbench(todayBlock, orderOverview, dishOverview);
        });
    }

    // ---------------------------------------------------------------- 内部

    /** 报表失败统一翻译成 500 {@code REPORT_STATISTICS_FAILED},前端能给出"统计加载失败"的专门提示。 */
    private <T> T reporting(java.util.function.Supplier<T> query) {
        try {
            return query.get();
        } catch (BusinessException ex) {
            throw ex;   // 日期区间等业务错误原样冒泡
        } catch (RuntimeException ex) {
            throw new BusinessException(InsightsErrorCode.REPORT_STATISTICS_FAILED);
        }
    }

    private static OverviewItem overview(String name, String title, long value) {
        return new OverviewItem(name, title, value);
    }

    private static DailyUserCount emptyUserCount() {
        DailyUserCount empty = new DailyUserCount();
        empty.setNewUserCount(0L);
        return empty;
    }

    private static <T> Map<LocalDate, T> indexById(List<T> rows, java.util.function.Function<T, LocalDate> key) {
        Map<LocalDate, T> map = new LinkedHashMap<>();
        for (T row : rows) {
            map.put(key.apply(row), row);
        }
        return map;
    }

    private int clampTopNumber(Integer topNumber) {
        if (topNumber == null || topNumber < 1) {
            return DEFAULT_TOP_NUMBER;
        }
        // 契约 §2.4 第 8 项:上限 20,超限钳制而不是报错
        return Math.min(topNumber, MAX_TOP_NUMBER);
    }

    private Range resolveRange(LocalDate beginDate, LocalDate endDate) {
        LocalDate begin = beginDate;
        LocalDate end = endDate;
        if (begin == null && end == null) {
            end = Times.nowLocal().toLocalDate();
            begin = end.minusDays(DEFAULT_RANGE_DAYS - 1L);
        }
        dateRangeValidator.validate(begin, end);
        return new Range(begin, end, begin.atStartOfDay(), end.plusDays(1).atStartOfDay());
    }

    /** 含首含尾的日期区间 + 内部使用的半开时间区间。 */
    private record Range(LocalDate beginDate, LocalDate endDate, LocalDateTime from, LocalDateTime to) {

        private List<LocalDate> days() {
            List<LocalDate> days = new ArrayList<>();
            for (LocalDate date = beginDate; !date.isAfter(endDate); date = date.plusDays(1)) {
                days.add(date);
            }
            return days;
        }
    }
}
