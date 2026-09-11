package com.sky.insights.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 报表聚合查询。
 *
 * <p><b>为什么报表直接查 orders / order_item / customer 等表,而不是走各上下文的服务:</b>
 * 报表是只读投影(D8),没有业务规则要复用;如果为每条聚合都在 order/identity/catalog 上开一个
 * 跨上下文方法,服务接口会被报表需求拽着长。这里刻意承担的代价是:**库表变了编译器不会提醒**,
 * 由 smoke 脚本与集成测试兜底(见 docs/02-database.md §5 给报表留的索引)。
 *
 * <p>查询口径按 02-database §5:报表按 **paid_at** 聚合(有 `idx_orders_paid_at`),
 * 有效订单 = status='COMPLETED'。
 */
@Mapper
public interface InsightsMapper {

    List<DailyTurnover> turnoverByDay(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    List<DailyOrderCount> ordersByDay(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    List<DailyUserCount> usersByDay(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    long countUsersUntil(@Param("until") LocalDateTime until);

    List<DishSales> topDishes(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                              @Param("topNumber") int topNumber);

    List<OrderStatusTotal> orderStatusTotals();

    long countOrdersPlacedBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    long countDishesByStatus(@Param("status") int status);

    long countSetmealsByStatus(@Param("status") int status);

    /** 今日零销量的在售菜品数(工作台"已售罄"角标)。 */
    long countZeroSalesOnSaleDishes(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
