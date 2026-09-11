package com.sky.order.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 未支付订单超时关单(领域 §4:15 分钟;契约 §2.4 第 3 项)。
 *
 * <p>只负责定时触发,真正的取消走 {@link OrderService#cancelTimedOutOrders()}——那里逐单过状态机,
 * 保证 R10 不被绕过。
 */
@Component
public class OrderTimeoutJob {

    private static final Logger log = LoggerFactory.getLogger(OrderTimeoutJob.class);

    private final OrderService orderService;

    public OrderTimeoutJob(OrderService orderService) {
        this.orderService = orderService;
    }

    /** 启动 30 秒后跑第一次,此后每分钟一次。 */
    @Scheduled(initialDelayString = "PT30S", fixedDelayString = "PT1M")
    public void closeTimedOutOrders() {
        int closed = orderService.cancelTimedOutOrders();
        if (closed > 0) {
            log.info("超时未支付自动关单:{} 单", closed);
        }
    }
}
