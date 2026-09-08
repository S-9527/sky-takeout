package com.sky.order.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sky.order.domain.OrderStateMachine;
import com.sky.order.entity.Orders;
import com.sky.order.enumeration.OrderStatus;
import com.sky.order.mapper.OrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;

/**
 * 定时任务类，定时处理订单状态
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class OrderTask {

    private final OrderMapper orderMapper;
    private final OrderStateMachine orderStateMachine;

    /**
     * 处理超时订单的方法
     */
    @Scheduled(cron = "0 * * * * ? ") //每分钟触发一次
    public void processTimeoutOrder(){
        log.info("定时处理超时订单：{}", LocalDateTime.now());

        LocalDateTime time = LocalDateTime.now().plusMinutes(-15);

        List<Orders> ordersList = orderMapper.selectList(new LambdaQueryWrapper<Orders>()
                .eq(Orders::getStatus, OrderStatus.PENDING_PAYMENT.getCode())
                .lt(Orders::getOrderTime, time));

        if(ordersList != null && ordersList.size() > 0){
            for (Orders orders : ordersList) {
                orderStateMachine.transition(orders, OrderStatus.CANCELLED, o -> {
                    o.setCancelReason("订单超时，自动取消");
                    o.setCancelTime(LocalDateTime.now());
                });
            }
        }
    }

    /**
     * 处理一直处于派送中状态的订单
     */
    @Scheduled(cron = "0 0 1 * * ?") //每天凌晨1点触发一次
    public void processDeliveryOrder(){
        log.info("定时处理处于派送中的订单：{}",LocalDateTime.now());

        LocalDateTime time = LocalDateTime.now().plusMinutes(-60);

        List<Orders> ordersList = orderMapper.selectList(new LambdaQueryWrapper<Orders>()
                .eq(Orders::getStatus, OrderStatus.DELIVERY_IN_PROGRESS.getCode())
                .lt(Orders::getOrderTime, time));

        if(ordersList != null && ordersList.size() > 0){
            for (Orders orders : ordersList) {
                orderStateMachine.transition(orders, OrderStatus.COMPLETED, null);
            }
        }
    }
}
