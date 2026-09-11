package com.sky.order.service;

import org.junit.jupiter.api.Test;

import com.sky.common.error.BusinessException;
import com.sky.order.domain.CancelSide;
import com.sky.order.domain.Order;
import com.sky.order.domain.OrderErrorCode;
import com.sky.order.domain.OrderStatus;
import com.sky.order.domain.PayStatus;
import com.sky.payment.domain.PaymentErrorCode;
import com.sky.payment.service.PaymentService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderCancellationServiceTest {

    private final OrderService orderService = mock(OrderService.class);
    private final PaymentService paymentService = mock(PaymentService.class);

    private final OrderCancellationService cancellationService =
            new OrderCancellationService(orderService, paymentService);

    private static Order order(OrderStatus status, PayStatus payStatus) {
        Order order = new Order();
        order.setId(4001L);
        order.setOrderNo("202501011200000001");
        order.setCustomerId(7L);
        order.setStatus(status);
        order.setPayStatus(payStatus);
        return order;
    }

    @Test
    void rejectUnpaidOrderJustCancels() {
        when(orderService.requireById(4001L)).thenReturn(order(OrderStatus.PENDING_ACCEPTANCE, PayStatus.UNPAID));

        cancellationService.reject(4001L, "菜品售完");

        verify(paymentService, never()).createRefund(any(), any(), any(), any());
        verify(orderService).markCancelled(4001L, CancelSide.MERCHANT, "菜品售完");
    }

    /** R6:已支付订单拒单必须先退款,退款成功后才落取消。 */
    @Test
    void rejectPaidOrderRefundsFirst() {
        when(orderService.requireById(4001L)).thenReturn(order(OrderStatus.PENDING_ACCEPTANCE, PayStatus.PAID));

        cancellationService.reject(4001L, "菜品售完");

        verify(paymentService).createRefund("202501011200000001", "菜品售完", "MERCHANT_REJECT", null);
        verify(orderService).markCancelled(4001L, CancelSide.MERCHANT, "菜品售完");
    }

    /** 退款受理失败 → 502 冒泡,订单状态不动(整个事务回滚)。 */
    @Test
    void rejectKeepsOrderWhenRefundFails() {
        when(orderService.requireById(4001L)).thenReturn(order(OrderStatus.PENDING_ACCEPTANCE, PayStatus.PAID));
        when(paymentService.createRefund(any(), any(), any(), any()))
                .thenThrow(new BusinessException(PaymentErrorCode.PAY_REFUND_FAILED));

        assertThatThrownBy(() -> cancellationService.reject(4001L, "菜品售完"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(PaymentErrorCode.PAY_REFUND_FAILED));
        verify(orderService, never()).markCancelled(any(), any(), any());
    }

    @Test
    void rejectOnlyAllowedFromPendingAcceptance() {
        when(orderService.requireById(4001L)).thenReturn(order(OrderStatus.ACCEPTED, PayStatus.PAID));

        assertThatThrownBy(() -> cancellationService.reject(4001L, "不想做了"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(OrderErrorCode.ORDER_INVALID_TRANSITION));
        verify(orderService, never()).markCancelled(any(), any(), any());
    }

    @Test
    void merchantCanCancelAcceptedPaidOrderWithRefund() {
        when(orderService.requireById(4001L)).thenReturn(order(OrderStatus.ACCEPTED, PayStatus.PAID));

        cancellationService.cancelByMerchant(4001L, "顾客电话取消");

        verify(paymentService).createRefund("202501011200000001", "顾客电话取消",
                "MERCHANT_CANCEL", null);
        verify(orderService).markCancelled(4001L, CancelSide.MERCHANT, "顾客电话取消");
    }

    /** 待付款是"迁移合法但只有顾客/系统能做" → ORDER_CANNOT_CANCEL。 */
    @Test
    void merchantCannotCancelPendingPaymentOrder() {
        when(orderService.requireById(4001L)).thenReturn(order(OrderStatus.PENDING_PAYMENT, PayStatus.UNPAID));

        assertThatThrownBy(() -> cancellationService.cancelByMerchant(4001L, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(OrderErrorCode.ORDER_CANNOT_CANCEL));
    }

    /** 已出餐不可取消:状态机不允许的迁移 → ORDER_INVALID_TRANSITION。 */
    @Test
    void merchantCannotCancelDeliveringOrder() {
        when(orderService.requireById(4001L)).thenReturn(order(OrderStatus.DELIVERING, PayStatus.PAID));

        assertThatThrownBy(() -> cancellationService.cancelByMerchant(4001L, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(OrderErrorCode.ORDER_INVALID_TRANSITION));
        verify(paymentService, never()).createRefund(any(), any(), any(), any());
    }

    @Test
    void unknownOrderPropagatesNotFound() {
        when(orderService.requireById(eq(999L)))
                .thenThrow(new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));

        assertThatThrownBy(() -> cancellationService.cancelByMerchant(999L, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(OrderErrorCode.ORDER_NOT_FOUND));
    }
}
