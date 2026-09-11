package com.sky.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

import com.sky.order.domain.Order;

@Mapper
public interface OrderMapper extends BaseMapper<Order> {

    /** 各状态订单数(一次 GROUP BY,不用逐个 count)。 */
    @Select("SELECT `status`, COUNT(*) AS `total` FROM `orders` GROUP BY `status`")
    List<OrderStatusCount> countByStatus();

    /**
     * 找出超时未支付的订单 id(定时关单用)。
     *
     * <p>只取 id,由 service 逐单走状态机取消:直接 UPDATE 会绕过 R10 的"迁移只能经状态机"。
     */
    @Select("""
            SELECT `id` FROM `orders`
            WHERE `status` = 'PENDING_PAYMENT' AND `placed_at` < #{deadline}
            ORDER BY `id`
            LIMIT #{limit}
            """)
    List<Long> selectTimedOutIds(@Param("deadline") LocalDateTime deadline, @Param("limit") int limit);
}
