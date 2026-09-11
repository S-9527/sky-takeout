package com.sky.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 当前的 {@link OrderNotifier} 实现:只打结构化日志。
 *
 * <p>WebSocket 推送(端点、会话管理、事务提交后触发)属于通知上下文,尚未实现;
 * 在它落地之前,催单**至少留下可检索的痕迹**,而不是静默丢弃。
 * 实现到位后这个类会被替换/降级为兜底日志,调用方(订单)无需改动。
 */
@Service
public class LoggingOrderNotifier implements OrderNotifier {

    private static final Logger log = LoggerFactory.getLogger(LoggingOrderNotifier.class);

    @Override
    public void orderReminder(Long orderId, String orderNo, String message) {
        log.info("催单通知(尚未接入 WebSocket): orderId={} orderNo={} message={}", orderId, orderNo, message);
    }
}
