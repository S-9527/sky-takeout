package com.sky.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

import com.sky.order.domain.OrderItem;

@Mapper
public interface OrderItemMapper extends BaseMapper<OrderItem> {

    /** 批量统计明细条数,供订单列表展示(避免 N+1)。 */
    @Select("""
            <script>
            SELECT `order_id`, COUNT(*) AS `item_count`
            FROM `order_item`
            WHERE `order_id` IN
            <foreach collection="orderIds" item="orderId" open="(" separator="," close=")">#{orderId}</foreach>
            GROUP BY `order_id`
            </script>
            """)
    List<OrderItemCount> countByOrderIds(@Param("orderIds") Collection<Long> orderIds);
}
