package com.sky.notification.service;

/**
 * 订单通知端口。订单/支付上下文只表达"要通知什么",推送细节(WebSocket、会话、集群广播)由通知上下文决定。
 *
 * <p>实现必须保证**事务提交后**才推送:否则事务回滚了,商家已经听到"有新订单"的提示音。
 */
public interface OrderNotifier {

    /** 来单提醒(支付成功、订单进入待接单)。 */
    void orderNew(OrderNewNotification order);

    /** 催单提醒。 */
    void orderReminder(Long orderId, String orderNo, String status, String message);
}
