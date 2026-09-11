package com.sky.payment.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.sky.common.domain.PageQuery;
import com.sky.common.domain.PageResponse;
import com.sky.common.domain.SortSpec;
import com.sky.common.error.BusinessException;
import com.sky.common.error.CommonErrorCode;
import com.sky.order.domain.OrderErrorCode;
import com.sky.notification.service.OrderNotifier;
import com.sky.order.service.OrderService;
import com.sky.payment.domain.Payment;
import com.sky.payment.domain.PaymentChannel;
import com.sky.payment.domain.PaymentErrorCode;
import com.sky.payment.domain.PaymentStatus;
import com.sky.payment.domain.Refund;
import com.sky.payment.domain.RefundReasonType;
import com.sky.payment.domain.RefundStatus;
import com.sky.payment.gateway.PaymentGateway;
import com.sky.payment.mapper.PaymentMapper;
import com.sky.payment.mapper.RefundMapper;
import com.sky.testsupport.TableInfoTestSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentServiceTest {

    private static final long CUSTOMER = 7L;
    private static final long ORDER_ID = 4001L;
    private static final String ORDER_NO = "202501011200000001";

    private final PaymentMapper paymentMapper = mock(PaymentMapper.class);
    private final RefundMapper refundMapper = mock(RefundMapper.class);
    private final PaymentGateway gateway = mock(PaymentGateway.class);
    private final OrderService orderService = mock(OrderService.class);
    private final RefundNoGenerator refundNoGenerator = mock(RefundNoGenerator.class);
    private final OrderNotifier orderNotifier = mock(OrderNotifier.class);

    private final PaymentService paymentService = new PaymentService(
            paymentMapper, refundMapper, gateway, orderService, refundNoGenerator, orderNotifier);

    @BeforeAll
    static void registerTableInfo() {
        TableInfoTestSupport.register(Payment.class, Refund.class);
    }

    /** 支付成功会组装来单提醒,这里给一个默认的订单投影。 */
    private void notificationViewAvailable() {
        when(orderService.notificationView(ORDER_ID)).thenReturn(new OrderService.OrderNotificationView(
                ORDER_ID, ORDER_NO, 5200L, "张三", "13800138000", "望京 1 号", 2, "不要香菜", null, null));
    }

    // ---------------------------------------------------------------- 辅助

    private static OrderService.OrderPaymentView order(String status, String payStatus, long payAmount) {
        return new OrderService.OrderPaymentView(ORDER_ID, ORDER_NO, status, payStatus, payAmount, null);
    }

    private static Payment payment(long id, PaymentStatus status, PaymentChannel channel) {
        Payment payment = new Payment();
        payment.setId(id);
        payment.setOrderId(ORDER_ID);
        payment.setOrderNo(ORDER_NO);
        payment.setChannel(channel);
        payment.setStatus(status);
        payment.setAmountCents(5200L);
        return payment;
    }

    private static Refund refund(long id, RefundStatus status) {
        Refund refund = new Refund();
        refund.setId(id);
        refund.setOrderId(ORDER_ID);
        refund.setPaymentId(6001L);
        refund.setRefundNo("RF202501011200000001");
        refund.setAmountCents(5200L);
        refund.setStatus(status);
        return refund;
    }

    private void gatewayIsMock(boolean settlesImmediately) {
        when(gateway.channel()).thenReturn(PaymentChannel.MOCK);
        when(gateway.settlesImmediately()).thenReturn(settlesImmediately);
        when(gateway.prepay(any(Payment.class), any())).thenReturn(
                new PaymentGateway.Prepay("mock_prepay", "1735704070", "nonce", "prepay_id=mock_prepay", "RSA", "sign"));
        when(gateway.refund(any(), any(Payment.class), anyLong(), any())).thenReturn(
                new PaymentGateway.RefundReceipt(true, "mock_refund_no", "ok"));
    }

    // ---------------------------------------------------------------- 发起支付

    @Test
    void startCreatesPaymentAndSettlesImmediatelyOnMockChannel() {
        when(orderService.requireOwnedPaymentView(CUSTOMER, ORDER_ID))
                .thenReturn(order("PENDING_PAYMENT", "UNPAID", 5200L));
        when(paymentMapper.selectOne(any())).thenReturn(null);
        when(paymentMapper.insert(any(Payment.class))).thenAnswer(invocation -> {
            ((Payment) invocation.getArgument(0)).setId(6001L);
            return 1;
        });
        gatewayIsMock(true);
        notificationViewAvailable();
        // applyPaySuccess 先读到 PENDING(刚插入的行),处理完再读就是 SUCCESS
        when(paymentMapper.selectById(6001L)).thenReturn(
                payment(6001L, PaymentStatus.PENDING, PaymentChannel.MOCK),
                payment(6001L, PaymentStatus.SUCCESS, PaymentChannel.MOCK));

        PaymentService.StartResult result = paymentService.start(CUSTOMER, ORDER_ID, PaymentChannel.MOCK, null);

        ArgumentCaptor<Payment> inserted = ArgumentCaptor.forClass(Payment.class);
        verify(paymentMapper).insert(inserted.capture());
        assertThat(inserted.getValue().getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(inserted.getValue().getAmountCents()).isEqualTo(5200L);
        assertThat(inserted.getValue().getOrderNo()).isEqualTo(ORDER_NO);

        // 第一次 update 写 prepayId,第二次才是"发起即成功"落库
        ArgumentCaptor<Payment> update = ArgumentCaptor.forClass(Payment.class);
        verify(paymentMapper, times(2)).updateById(update.capture());
        assertThat(update.getAllValues().get(0).getPrepayId()).isEqualTo("mock_prepay");
        Payment settled = update.getAllValues().get(1);
        assertThat(settled.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(settled.getTransactionId()).isEqualTo("MOCK-6001");
        verify(orderService).markPaid(ORDER_ID, "MOCK");

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.payAmountCents()).isEqualTo(5200L);
        assertThat(result.mockPayUrl()).isNull();
    }

    @Test
    void startRejectsAlreadyPaidOrder() {
        when(orderService.requireOwnedPaymentView(CUSTOMER, ORDER_ID))
                .thenReturn(order("PENDING_ACCEPTANCE", "PAID", 5200L));

        assertThatThrownBy(() -> paymentService.start(CUSTOMER, ORDER_ID, PaymentChannel.MOCK, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(PaymentErrorCode.PAY_DUPLICATE_PAYMENT));
        verify(paymentMapper, never()).insert(any(Payment.class));
    }

    @Test
    void startRejectsCancelledOrder() {
        when(orderService.requireOwnedPaymentView(CUSTOMER, ORDER_ID))
                .thenReturn(order("CANCELLED", "UNPAID", 5200L));

        assertThatThrownBy(() -> paymentService.start(CUSTOMER, ORDER_ID, PaymentChannel.MOCK, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(PaymentErrorCode.PAY_ORDER_NOT_PAYABLE));
    }

    @Test
    void startRejectsUnavailableChannel() {
        when(orderService.requireOwnedPaymentView(CUSTOMER, ORDER_ID))
                .thenReturn(order("PENDING_PAYMENT", "UNPAID", 5200L));
        when(gateway.channel()).thenReturn(PaymentChannel.MOCK);

        assertThatThrownBy(() -> paymentService.start(CUSTOMER, ORDER_ID, PaymentChannel.WECHAT, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(CommonErrorCode.COMMON_VALIDATION_FAILED));
    }

    @Test
    void startReusesExistingPendingPayment() {
        when(orderService.requireOwnedPaymentView(CUSTOMER, ORDER_ID))
                .thenReturn(order("PENDING_PAYMENT", "UNPAID", 5200L));
        when(paymentMapper.selectOne(any())).thenReturn(payment(6001L, PaymentStatus.PENDING, PaymentChannel.MOCK));
        gatewayIsMock(false);

        PaymentService.StartResult result = paymentService.start(CUSTOMER, ORDER_ID, PaymentChannel.MOCK, "openid");

        assertThat(result.paymentId()).isEqualTo(6001L);
        assertThat(result.status()).isEqualTo("PENDING");
        verify(paymentMapper, never()).insert(any(Payment.class));
        verify(orderService, never()).markPaid(anyLong(), any());
    }

    @Test
    void startPropagatesOrderNotFoundForForeignOrder() {
        when(orderService.requireOwnedPaymentView(CUSTOMER, ORDER_ID))
                .thenThrow(new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));

        assertThatThrownBy(() -> paymentService.start(CUSTOMER, ORDER_ID, PaymentChannel.MOCK, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(OrderErrorCode.ORDER_NOT_FOUND));
    }

    // ---------------------------------------------------------------- 支付成功(R7)

    @Test
    void applyPaySuccessIsIdempotentForSucceededPayment() {
        when(paymentMapper.selectById(6001L)).thenReturn(payment(6001L, PaymentStatus.SUCCESS, PaymentChannel.MOCK));

        assertThat(paymentService.applyPaySuccess(6001L, "TXN-1", null)).isFalse();
        verify(paymentMapper, never()).updateById(any(Payment.class));
        verify(orderService, never()).markPaid(anyLong(), any());
    }

    @Test
    void applyPaySuccessIgnoresDuplicateTransactionId() {
        when(paymentMapper.selectById(6001L)).thenReturn(payment(6001L, PaymentStatus.PENDING, PaymentChannel.MOCK));
        when(paymentMapper.exists(any())).thenReturn(true);

        assertThat(paymentService.applyPaySuccess(6001L, "TXN-1", null)).isFalse();
        verify(paymentMapper, never()).updateById(any(Payment.class));
    }

    @Test
    void applyPaySuccessMarksOrderPaid() {
        when(paymentMapper.selectById(6001L)).thenReturn(payment(6001L, PaymentStatus.PENDING, PaymentChannel.WECHAT));
        when(paymentMapper.exists(any())).thenReturn(false);
        notificationViewAvailable();

        assertThat(paymentService.applyPaySuccess(6001L, "TXN-1", "{\"raw\":true}")).isTrue();

        ArgumentCaptor<Payment> update = ArgumentCaptor.forClass(Payment.class);
        verify(paymentMapper).updateById(update.capture());
        assertThat(update.getValue().getTransactionId()).isEqualTo("TXN-1");
        assertThat(update.getValue().getPaidAt()).isNotNull();
        verify(orderService).markPaid(ORDER_ID, "WECHAT");
        // 来单提醒在支付成功后被触发(实际推送由 notifier 保证提交后发出)
        verify(orderNotifier).orderNew(any());
    }

    @Test
    void applyPaySuccessRejectsUnknownPayment() {
        when(paymentMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> paymentService.applyPaySuccess(999L, "TXN", null))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(PaymentErrorCode.PAY_NOT_FOUND));
    }

    // ---------------------------------------------------------------- 查询

    @Test
    void statusReturnsNullPaymentWhenNothingStarted() {
        when(orderService.requireOwnedPaymentView(CUSTOMER, ORDER_ID))
                .thenReturn(order("PENDING_PAYMENT", "UNPAID", 5200L));
        when(paymentMapper.selectOne(any())).thenReturn(null);

        PaymentService.StatusView view = paymentService.status(CUSTOMER, ORDER_ID);

        assertThat(view.payment()).isNull();
        assertThat(view.orderStatus()).isEqualTo("PENDING_PAYMENT");
        assertThat(view.payStatus()).isEqualTo("UNPAID");
    }

    @Test
    void statusMapsLatestPayment() {
        when(orderService.requireOwnedPaymentView(CUSTOMER, ORDER_ID))
                .thenReturn(order("PENDING_ACCEPTANCE", "PAID", 5200L));
        when(paymentMapper.selectOne(any())).thenReturn(payment(6001L, PaymentStatus.SUCCESS, PaymentChannel.MOCK));

        PaymentService.StatusView view = paymentService.status(CUSTOMER, ORDER_ID);

        assertThat(view.payment().id()).isEqualTo(6001L);
        assertThat(view.payment().status()).isEqualTo("SUCCESS");
        assertThat(view.payment().channel()).isEqualTo("MOCK");
    }

    // ---------------------------------------------------------------- 退款

    private void refundPreconditions(String orderStatus, String payStatus, boolean hasOpenRefund, boolean hasPayment) {
        when(orderService.findPaymentViewByOrderNo(ORDER_NO))
                .thenReturn(Optional.of(order(orderStatus, payStatus, 5200L)));
        when(refundMapper.exists(any())).thenReturn(hasOpenRefund);
        when(paymentMapper.selectOne(any())).thenReturn(hasPayment
                ? payment(6001L, PaymentStatus.SUCCESS, PaymentChannel.MOCK) : null);
    }

    @Test
    void createRefundRejectsUnknownOrder() {
        when(orderService.findPaymentViewByOrderNo("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.createRefund("nope", "原因", null, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(PaymentErrorCode.PAY_ORDER_NOT_FOUND));
    }

    @Test
    void createRefundRejectsUnpaidOrder() {
        refundPreconditions("PENDING_PAYMENT", "UNPAID", false, false);

        assertThatThrownBy(() -> paymentService.createRefund(ORDER_NO, "原因", null, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(PaymentErrorCode.PAY_ORDER_NOT_PAID));
    }

    /** R8:已完成订单不可退款。 */
    @Test
    void createRefundRejectsCompletedOrder() {
        refundPreconditions("COMPLETED", "PAID", false, true);

        assertThatThrownBy(() -> paymentService.createRefund(ORDER_NO, "原因", null, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(PaymentErrorCode.PAY_ORDER_NOT_REFUNDABLE));
    }

    @Test
    void createRefundRejectsAmountMismatch() {
        refundPreconditions("ACCEPTED", "PAID", false, true);

        assertThatThrownBy(() -> paymentService.createRefund(ORDER_NO, "原因", null, 9999L))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.errorCode()).isEqualTo(PaymentErrorCode.PAY_REFUND_AMOUNT_EXCEEDED);
                    assertThat(ex.details()).isNotEmpty();
                });
    }

    @Test
    void createRefundRejectsExistingOpenOrSucceededRefund() {
        refundPreconditions("ACCEPTED", "PAID", true, true);

        assertThatThrownBy(() -> paymentService.createRefund(ORDER_NO, "原因", null, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(PaymentErrorCode.PAY_REFUND_ALREADY_EXISTS));
        verify(refundMapper, never()).insert(any(Refund.class));
    }

    @Test
    void createRefundRejectsWhenNoSuccessfulPayment() {
        refundPreconditions("ACCEPTED", "PAID", false, false);

        assertThatThrownBy(() -> paymentService.createRefund(ORDER_NO, "原因", null, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(PaymentErrorCode.PAY_ORDER_NOT_PAID));
    }

    /** R6:渠道受理失败 → 502,且**不落任何退款记录**,订单状态与 payStatus 不变。 */
    @Test
    void createRefundFailsWhenChannelRejects() {
        refundPreconditions("ACCEPTED", "PAID", false, true);
        when(refundNoGenerator.next()).thenReturn("RF202501011200000001");
        when(gateway.refund(any(), any(Payment.class), anyLong(), any()))
                .thenReturn(new PaymentGateway.RefundReceipt(false, null, "渠道不可用"));

        assertThatThrownBy(() -> paymentService.createRefund(ORDER_NO, "原因", "MERCHANT_REJECT", null))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.errorCode()).isEqualTo(PaymentErrorCode.PAY_REFUND_FAILED);
                    assertThat(ex.errorCode().httpStatus()).isEqualTo(org.springframework.http.HttpStatus.BAD_GATEWAY);
                });
        verify(refundMapper, never()).insert(any(Refund.class));
        verify(orderService, never()).markRefunded(anyLong());
    }

    @Test
    void createRefundSettlesImmediatelyOnMockChannel() {
        refundPreconditions("ACCEPTED", "PAID", false, true);
        when(refundNoGenerator.next()).thenReturn("RF202501011200000001");
        gatewayIsMock(true);
        when(refundMapper.insert(any(Refund.class))).thenAnswer(invocation -> {
            ((Refund) invocation.getArgument(0)).setId(7001L);
            return 1;
        });
        // applyRefundSuccess 先读到 PENDING(刚插入的行),处理完再读就是 SUCCESS
        when(refundMapper.selectById(7001L)).thenReturn(
                refund(7001L, RefundStatus.PENDING),
                refund(7001L, RefundStatus.SUCCESS));

        PaymentService.RefundView view = paymentService.createRefund(
                ORDER_NO, "顾客电话要求取消", "CUSTOMER_APPLY", 5200L);

        ArgumentCaptor<Refund> inserted = ArgumentCaptor.forClass(Refund.class);
        verify(refundMapper).insert(inserted.capture());
        assertThat(inserted.getValue().getRefundNo()).isEqualTo("RF202501011200000001");
        assertThat(inserted.getValue().getAmountCents()).isEqualTo(5200L);
        assertThat(inserted.getValue().getReasonType()).isEqualTo(RefundReasonType.CUSTOMER_APPLY);

        ArgumentCaptor<Refund> update = ArgumentCaptor.forClass(Refund.class);
        verify(refundMapper).updateById(update.capture());
        assertThat(update.getValue().getStatus()).isEqualTo(RefundStatus.SUCCESS);
        assertThat(update.getValue().getRefundedAt()).isNotNull();
        verify(orderService).markRefunded(ORDER_ID);

        assertThat(view.refund().getStatus()).isEqualTo(RefundStatus.SUCCESS);
        assertThat(view.orderNo()).isEqualTo(ORDER_NO);
    }

    @Test
    void applyRefundSuccessIsIdempotentAndMarksOrderRefunded() {
        when(refundMapper.selectById(7001L)).thenReturn(refund(7001L, RefundStatus.SUCCESS));
        assertThat(paymentService.applyRefundSuccess(7001L, null)).isFalse();

        when(refundMapper.selectById(7002L)).thenReturn(refund(7002L, RefundStatus.PENDING));
        assertThat(paymentService.applyRefundSuccess(7002L, "{\"ok\":true}")).isTrue();
        verify(orderService).markRefunded(ORDER_ID);
    }

    @Test
    void applyRefundSuccessRejectsUnknownRefund() {
        when(refundMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> paymentService.applyRefundSuccess(999L, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(PaymentErrorCode.PAY_REFUND_NOT_FOUND));
    }

    // ---------------------------------------------------------------- 退款列表

    @Test
    void pageRefundsAppliesFiltersAndFillsOrderNo() {
        Page<Refund> page = new Page<>(1, 20);
        page.setRecords(List.of(refund(7001L, RefundStatus.SUCCESS)));
        page.setTotal(1);
        when(refundMapper.selectPage(any(), any())).thenReturn(page);
        when(orderService.findPaymentViewByOrderNo(ORDER_NO))
                .thenReturn(Optional.of(order("ACCEPTED", "PAID", 5200L)));
        when(orderService.orderNosByIds(any())).thenReturn(Map.of(ORDER_ID, ORDER_NO));

        PageResponse<PaymentService.RefundView> result = paymentService.pageRefunds(
                RefundStatus.SUCCESS, ORDER_NO, null, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31),
                PageQuery.of(1, 20), new SortSpec("createdAt", true));

        assertThat(result.records()).hasSize(1);
        assertThat(result.records().get(0).orderNo()).isEqualTo(ORDER_NO);
        verify(orderService).orderNosByIds(any());
    }

    @Test
    void pageRefundsWithUnknownOrderNoReturnsEmptyPage() {
        when(orderService.findPaymentViewByOrderNo("nope")).thenReturn(Optional.empty());

        PageResponse<PaymentService.RefundView> result = paymentService.pageRefunds(
                null, "nope", null, null, null, PageQuery.of(2, 30), new SortSpec("createdAt", true));

        assertThat(result.records()).isEmpty();
        assertThat(result.page()).isEqualTo(2);
        assertThat(result.pageSize()).isEqualTo(30);
        verify(refundMapper, never()).selectPage(any(), any());
    }

    @Test
    void pageRefundsRejectsUnknownSortField() {
        assertThatThrownBy(() -> paymentService.pageRefunds(null, null, null, null, null,
                PageQuery.of(1, 20), new SortSpec("id", false)))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(CommonErrorCode.COMMON_SORT_FIELD_NOT_ALLOWED));
    }

    @Test
    void pageRefundsSupportsEveryWhitelistedSortField() {
        Page<Refund> page = new Page<>(1, 20);
        page.setRecords(List.of());
        page.setTotal(0);
        when(refundMapper.selectPage(any(), any())).thenReturn(page);

        for (String field : PaymentService.REFUND_SORT_WHITELIST) {
            paymentService.pageRefunds(null, null, null, null, null, PageQuery.of(1, 20),
                    new SortSpec(field, true));
        }

        assertThat(PaymentService.REFUND_SORT_WHITELIST)
                .containsExactlyInAnyOrder("createdAt", "refundedAt", "amountCents");
    }
}
