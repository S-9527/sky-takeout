package com.sky.payment.gateway;

import com.sky.payment.domain.Payment;
import com.sky.payment.domain.PaymentChannel;

/**
 * 支付渠道端口。本地联调不依赖真实商户号:靠配置切换实现(后端架构 §4.7)。
 *
 * <p>约束与微信登录一样:真实实现不得让应用启动失败在"第一次调用"上——缺少配置时要么不注册,
 * 要么在启动阶段就说清楚。
 */
public interface PaymentGateway {

    PaymentChannel channel();

    /**
     * 该渠道是否"发起即成功"。本地 mock 通道为 true:发起支付后立即完成回调语义
     * (README 的 {@code SKY_PAYMENT_GATEWAY=mock} 行为),这样不接微信也能跑通下单→支付→退款全链路。
     */
    boolean settlesImmediately();

    /** 预下单:返回小程序调起支付所需参数。 */
    Prepay prepay(Payment payment, String payerOpenid);

    /** 发起退款受理。{@code accepted=false} 时上层抛 502 {@code PAY_REFUND_FAILED},不落任何退款状态。 */
    RefundReceipt refund(String refundNo, Payment payment, long amountCents, String reason);

    record Prepay(String prepayId, String timeStamp, String nonceStr, String packageValue,
                  String signType, String paySign) {
    }

    record RefundReceipt(boolean accepted, String channelRefundNo, String message) {
    }
}
