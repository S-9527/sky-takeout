package com.sky.order.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import com.sky.common.error.BusinessException;
import com.sky.common.error.ErrorResponse;
import com.sky.order.domain.CancelSide;
import com.sky.order.domain.Order;
import com.sky.order.domain.OrderErrorCode;
import com.sky.order.domain.OrderStateMachine;
import com.sky.order.domain.OrderStatus;
import com.sky.payment.service.PaymentService;

/**
 * 商家侧取消(拒单 / 取消订单),以及 R6 要求的"已支付必须退款"。
 *
 * <p>为什么单独一个 service:退款在 payment 上下文,而 payment 已经依赖 order(付款成功要迁移订单状态)。
 * 如果让 {@link OrderService} 反过来依赖 payment,两个 bean 就构成循环依赖。这里做"编排者":
 * 它同时依赖两边,而两边互不依赖。
 *
 * <p>退款原因分类用字符串传递(取值即 {@code RefundReasonType} 的名字):跨上下文只能用对方的 service 包,
 * 引用 payment 的 domain 枚举会被架构测试打红(L4)。
 */
@Service
public class OrderCancellationService {

    private final OrderService orderService;
    private final PaymentService paymentService;

    public OrderCancellationService(OrderService orderService, PaymentService paymentService) {
        this.orderService = orderService;
        this.paymentService = paymentService;
    }

    /** 商家拒单:仅 PENDING_ACCEPTANCE 可拒;已支付必须先退款(R6)。 */
    @Transactional
    public void reject(Long orderId, String reason) {
        Order order = orderService.requireById(orderId);
        if (order.getStatus() != OrderStatus.PENDING_ACCEPTANCE) {
            throw new BusinessException(OrderErrorCode.ORDER_INVALID_TRANSITION,
                    "订单当前状态不允许该操作",
                    List.of(new ErrorResponse.Detail("status",
                            "拒单只允许 PENDING_ACCEPTANCE,当前为 " + order.getStatus())));
        }
        refundIfPaid(order, reason, "MERCHANT_REJECT");
        orderService.markCancelled(orderId, CancelSide.MERCHANT, reason);
    }

    /**
     * 商家取消订单:仅 PENDING_ACCEPTANCE / ACCEPTED。
     *
     * <p>两种情况分得很清楚:{@code DELIVERING} 等状态是**状态机不允许的迁移** → {@code ORDER_INVALID_TRANSITION};
     * 待付款是迁移合法但只有顾客/系统能做 → {@code ORDER_CANNOT_CANCEL}。
     */
    @Transactional
    public void cancelByMerchant(Long orderId, String reason) {
        Order order = orderService.requireById(orderId);
        OrderStateMachine.requireTransition(order.getStatus(), OrderStatus.CANCELLED);
        if (order.getStatus() != OrderStatus.PENDING_ACCEPTANCE && order.getStatus() != OrderStatus.ACCEPTED) {
            throw new BusinessException(OrderErrorCode.ORDER_CANNOT_CANCEL);
        }
        refundIfPaid(order, reason, "MERCHANT_CANCEL");
        orderService.markCancelled(orderId, CancelSide.MERCHANT, reason);
    }

    /**
     * 已支付订单取消前必须发起退款:退款受理失败时 {@code PaymentService} 抛 502,
     * 整个事务回滚——订单状态与 {@code payStatus} 都保持原样(R6:不允许"假取消")。
     */
    private void refundIfPaid(Order order, String reason, String reasonType) {
        if (order.getPayStatus() != null && order.getPayStatus().isPaid()) {
            paymentService.createRefund(order.getOrderNo(), reason, reasonType, null);
        }
    }
}
