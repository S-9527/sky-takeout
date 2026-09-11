package com.sky.notification.service;

/**
 * 订单通知端口。订单上下文在"新订单/催单"时调用它,推送细节由通知上下文决定
 * (契约第 4 节:WebSocket 端点 {@code /ws/admin},消息类型 {@code ORDER_NEW} / {@code ORDER_REMIND})。
 *
 * <p>抽出接口是为了让订单上下文不依赖 WebSocket:推送通道属于通知上下文,
 * 订单只知道"要通知"这件事。
 */
public interface OrderNotifier {

    /** 催单通知。 */
    void orderReminder(Long orderId, String orderNo, String message);
}
