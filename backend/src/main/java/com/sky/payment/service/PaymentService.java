package com.sky.payment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.sky.common.domain.PageQuery;
import com.sky.common.domain.PageResponse;
import com.sky.common.domain.SortSpec;
import com.sky.common.error.BusinessException;
import com.sky.common.error.CommonErrorCode;
import com.sky.common.error.ErrorResponse;
import com.sky.common.util.Json;
import com.sky.common.util.Times;
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

/**
 * 支付与退款。
 *
 * <p>关键规则:
 * <ul>
 *   <li><b>R7 幂等</b>:以 {@code transaction_id} 为幂等键——同一笔支付重复通知只生效一次;</li>
 *   <li><b>R6 不假取消</b>:退款受理失败 → 502 {@code PAY_REFUND_FAILED},订单状态与 payStatus 都不变
 *       (所以退款记录只在渠道受理成功后才落库);</li>
 *   <li><b>R8</b>:{@code COMPLETED} 订单不可退款;</li>
 *   <li>v2 只做**整单全额退**,金额由服务端取订单实付,不接受请求体里的金额(只用于一致性校验)。</li>
 * </ul>
 * 订单字段一律通过 {@link OrderService} 的跨上下文投影读写,不引用 order 的实体(L4)。
 */
@Service
public class PaymentService {

    /** 与 openapi 的 pageRefunds 描述一致。 */
    public static final Set<String> REFUND_SORT_WHITELIST = Set.of("createdAt", "refundedAt", "amountCents");

    private static final String PAY_STATUS_PAID = "PAID";
    private static final String PAY_STATUS_PARTIAL_REFUNDED = "PARTIAL_REFUNDED";
    private static final String ORDER_STATUS_PENDING_PAYMENT = "PENDING_PAYMENT";
    private static final String ORDER_STATUS_COMPLETED = "COMPLETED";

    private final PaymentMapper paymentMapper;
    private final RefundMapper refundMapper;
    private final PaymentGateway paymentGateway;
    private final OrderService orderService;
    private final RefundNoGenerator refundNoGenerator;

    public PaymentService(PaymentMapper paymentMapper, RefundMapper refundMapper, PaymentGateway paymentGateway,
                          OrderService orderService, RefundNoGenerator refundNoGenerator) {
        this.paymentMapper = paymentMapper;
        this.refundMapper = refundMapper;
        this.paymentGateway = paymentGateway;
        this.orderService = orderService;
        this.refundNoGenerator = refundNoGenerator;
    }

    /** 发起支付的结果:支付参数 + 当前支付状态。 */
    public record StartResult(Long paymentId, String channel, String status, long payAmountCents,
                              String timeStamp, String nonceStr, String packageValue, String signType,
                              String paySign, String mockPayUrl) {
    }

    public record PaymentView(Long id, Long orderId, String orderNo, String channel, String status,
                              long amountCents, String transactionId, String prepayId,
                              LocalDateTime paidAt, LocalDateTime createdAt) {
    }

    public record StatusView(Long orderId, String orderNo, String orderStatus, String payStatus,
                             PaymentView payment) {
    }

    /** 退款记录 + 订单号(订单号在订单表上,列表展示需要,批量补以免 N+1)。 */
    public record RefundView(Refund refund, String orderNo) {
    }

    // ---------------------------------------------------------------- 发起支付

    /**
     * 顾客对本人待付款订单发起支付。
     *
     * <p>幂等:同一订单已有一笔未完成的支付时复用它的 {@code prepayId},不重复建流水。
     * mock 通道"发起即成功",因此本地联调可以直接走到"待接单"并接着退款。
     */
    @Transactional
    public StartResult start(Long customerId, Long orderId, PaymentChannel channel, String payerOpenid) {
        OrderService.OrderPaymentView order = orderService.requireOwnedPaymentView(customerId, orderId);

        if (isPaid(order.payStatus())) {
            throw new BusinessException(PaymentErrorCode.PAY_DUPLICATE_PAYMENT);
        }
        if (!ORDER_STATUS_PENDING_PAYMENT.equals(order.status())) {
            // 已取消 / 超时关单的订单不能支付
            throw new BusinessException(PaymentErrorCode.PAY_ORDER_NOT_PAYABLE);
        }
        if (channel != null && channel != paymentGateway.channel()) {
            throw new BusinessException(CommonErrorCode.COMMON_VALIDATION_FAILED,
                    "该支付渠道当前不可用",
                    List.of(new ErrorResponse.Detail("channel", "当前部署只支持 " + paymentGateway.channel())));
        }

        Payment payment = findOpenPayment(orderId);
        if (payment == null) {
            payment = new Payment();
            payment.setOrderId(order.id());
            payment.setOrderNo(order.orderNo());
            payment.setChannel(paymentGateway.channel());
            payment.setStatus(PaymentStatus.PENDING);
            payment.setAmountCents(order.payAmountCents());
            paymentMapper.insert(payment);

            PaymentGateway.Prepay prepay = paymentGateway.prepay(payment, payerOpenid);
            Payment update = new Payment();
            update.setId(payment.getId());
            update.setPrepayId(prepay.prepayId());
            paymentMapper.updateById(update);
            payment.setPrepayId(prepay.prepayId());

            if (paymentGateway.settlesImmediately()) {
                applyPaySuccess(payment.getId(), "MOCK-" + payment.getId(),
                        Json.write(Map.of("channel", "MOCK", "paymentId", payment.getId())));
            }
            payment = paymentMapper.selectById(payment.getId());
            return toStartResult(payment, prepay);
        }

        // 复用未完成的支付:重新取一次支付参数,不新建流水
        PaymentGateway.Prepay prepay = paymentGateway.prepay(payment, payerOpenid);
        return toStartResult(payment, prepay);
    }

    /** 顾客轮询支付结果。订单尚未发起支付时 {@code payment} 为 null。 */
    public StatusView status(Long customerId, Long orderId) {
        OrderService.OrderPaymentView order = orderService.requireOwnedPaymentView(customerId, orderId);
        Payment payment = latestPayment(orderId);
        return new StatusView(order.id(), order.orderNo(), order.status(), order.payStatus(),
                payment == null ? null : toView(payment));
    }

    /**
     * 支付成功落库(R7 幂等)。
     *
     * @return true 表示这次真的生效了;false 表示是重复通知
     */
    @Transactional
    public boolean applyPaySuccess(Long paymentId, String transactionId, String rawNotify) {
        Payment payment = paymentMapper.selectById(paymentId);
        if (payment == null) {
            throw new BusinessException(PaymentErrorCode.PAY_NOT_FOUND);
        }
        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            return false;
        }
        if (transactionId != null && paymentMapper.exists(Wrappers.<Payment>lambdaQuery()
                .eq(Payment::getTransactionId, transactionId)
                .ne(Payment::getId, paymentId))) {
            return false;
        }

        Payment update = new Payment();
        update.setId(paymentId);
        update.setStatus(PaymentStatus.SUCCESS);
        update.setTransactionId(transactionId);
        update.setPaidAt(Times.nowLocal());
        update.setRawNotify(rawNotify);
        paymentMapper.updateById(update);

        orderService.markPaid(payment.getOrderId(), payment.getChannel().name());
        return true;
    }

    // ---------------------------------------------------------------- 退款

    /**
     * 管理端发起整单全额退。
     *
     * <p>渠道**受理成功**才落退款记录:受理失败直接 502,订单状态与 payStatus 不变(R6)。
     */
    @Transactional
    public RefundView createRefund(String orderNo, String reason, String reasonTypeName, Long expectedAmountCents) {
        RefundReasonType reasonType = parseReasonType(reasonTypeName);
        OrderService.OrderPaymentView order = orderService.findPaymentViewByOrderNo(orderNo)
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.PAY_ORDER_NOT_FOUND));

        // 校验顺序按"回答最精确"排:重复退款先说"已申请过"(409),再说未支付/已完成/金额不符。
        // 退款成功会把订单 payStatus 置 REFUNDED,如果先判"是否已支付",重复退款会被误报成
        // PAY_ORDER_NOT_PAID,与契约里那个 409 的语义对不上。
        if (refundMapper.exists(Wrappers.<Refund>lambdaQuery()
                .eq(Refund::getOrderId, order.id())
                .in(Refund::getStatus, RefundStatus.PENDING, RefundStatus.SUCCESS))) {
            throw new BusinessException(PaymentErrorCode.PAY_REFUND_ALREADY_EXISTS);
        }
        if (!isPaid(order.payStatus())) {
            throw new BusinessException(PaymentErrorCode.PAY_ORDER_NOT_PAID);
        }
        if (ORDER_STATUS_COMPLETED.equals(order.status())) {
            throw new BusinessException(PaymentErrorCode.PAY_ORDER_NOT_REFUNDABLE);
        }
        if (expectedAmountCents != null && expectedAmountCents != order.payAmountCents()) {
            throw new BusinessException(PaymentErrorCode.PAY_REFUND_AMOUNT_EXCEEDED,
                    "退款金额不正确",
                    List.of(new ErrorResponse.Detail("expectedAmountCents",
                            "订单实付为 " + order.payAmountCents() + " 分")));
        }

        Payment payment = requireSuccessfulPayment(order.id());
        String refundNo = refundNoGenerator.next();
        PaymentGateway.RefundReceipt receipt = paymentGateway.refund(refundNo, payment, order.payAmountCents(), reason);
        if (!receipt.accepted()) {
            throw new BusinessException(PaymentErrorCode.PAY_REFUND_FAILED,
                    "退款受理失败,请稍后重试",
                    List.of(new ErrorResponse.Detail("refundNo", receipt.message())));
        }

        Refund refund = new Refund();
        refund.setOrderId(order.id());
        refund.setPaymentId(payment.getId());
        refund.setRefundNo(refundNo);
        refund.setAmountCents(order.payAmountCents());
        refund.setStatus(RefundStatus.PENDING);
        refund.setReason(reason);
        refund.setReasonType(reasonType == null ? RefundReasonType.OTHER : reasonType);
        refundMapper.insert(refund);

        if (paymentGateway.settlesImmediately()) {
            applyRefundSuccess(refund.getId(),
                    Json.write(Map.of("channel", paymentGateway.channel().name(),
                            "refundNo", refundNo,
                            "channelRefundNo", String.valueOf(receipt.channelRefundNo()))));
        }
        return new RefundView(requireRefund(refund.getId()), order.orderNo());
    }

    /** 退款成功落库(幂等):更新流水并置订单 {@code payStatus=REFUNDED}。 */
    @Transactional
    public boolean applyRefundSuccess(Long refundId, String rawNotify) {
        Refund refund = refundMapper.selectById(refundId);
        if (refund == null) {
            throw new BusinessException(PaymentErrorCode.PAY_REFUND_NOT_FOUND);
        }
        if (refund.getStatus() == RefundStatus.SUCCESS) {
            return false;
        }
        Refund update = new Refund();
        update.setId(refundId);
        update.setStatus(RefundStatus.SUCCESS);
        update.setRefundedAt(Times.nowLocal());
        update.setRawNotify(rawNotify);
        refundMapper.updateById(update);

        orderService.markRefunded(refund.getOrderId());
        return true;
    }

    // ---------------------------------------------------------------- 查询

    public PageResponse<RefundView> pageRefunds(RefundStatus status, String orderNo, String refundNo,
                                                LocalDate beginDate, LocalDate endDate,
                                                PageQuery pageQuery, SortSpec sortSpec) {
        LambdaQueryWrapper<Refund> wrapper = Wrappers.lambdaQuery();
        if (status != null) {
            wrapper.eq(Refund::getStatus, status);
        }
        if (StringUtils.hasText(refundNo)) {
            wrapper.eq(Refund::getRefundNo, refundNo);
        }
        if (StringUtils.hasText(orderNo)) {
            // 订单号不在退款表上:先按订单号定位订单,再按 order_id 过滤;订单不存在就是空页
            java.util.Optional<OrderService.OrderPaymentView> order = orderService.findPaymentViewByOrderNo(orderNo);
            if (order.isEmpty()) {
                return PageResponse.empty(pageQuery.page(), pageQuery.pageSize());
            }
            wrapper.eq(Refund::getOrderId, order.get().id());
        }
        if (beginDate != null) {
            wrapper.ge(Refund::getCreatedAt, beginDate.atStartOfDay());
        }
        if (endDate != null) {
            wrapper.lt(Refund::getCreatedAt, endDate.plusDays(1).atStartOfDay());
        }
        applyRefundSort(wrapper, sortSpec);

        Page<Refund> page = refundMapper.selectPage(new Page<>(pageQuery.page(), pageQuery.pageSize()), wrapper);
        Map<Long, String> orderNos = orderService.orderNosByIds(
                page.getRecords().stream().map(Refund::getOrderId).distinct().toList());
        return PageResponse.from(page).map(refund -> new RefundView(refund, orderNos.get(refund.getOrderId())));
    }

    public Refund requireRefund(Long id) {
        Refund refund = refundMapper.selectById(id);
        if (refund == null) {
            throw new BusinessException(PaymentErrorCode.PAY_REFUND_NOT_FOUND);
        }
        return refund;
    }

    public List<Refund> refundsOf(Long orderId) {
        return refundMapper.selectList(Wrappers.<Refund>lambdaQuery()
                .eq(Refund::getOrderId, orderId)
                .orderByAsc(Refund::getId));
    }

    public List<Payment> paymentsOf(Long orderId) {
        return paymentMapper.selectList(Wrappers.<Payment>lambdaQuery()
                .eq(Payment::getOrderId, orderId)
                .orderByAsc(Payment::getId));
    }

    // ---------------------------------------------------------------- 内部

    private Payment findOpenPayment(Long orderId) {
        return paymentMapper.selectOne(Wrappers.<Payment>lambdaQuery()
                .eq(Payment::getOrderId, orderId)
                .eq(Payment::getStatus, PaymentStatus.PENDING)
                .orderByDesc(Payment::getId)
                .last("LIMIT 1"));
    }

    private Payment latestPayment(Long orderId) {
        return paymentMapper.selectOne(Wrappers.<Payment>lambdaQuery()
                .eq(Payment::getOrderId, orderId)
                .orderByDesc(Payment::getId)
                .last("LIMIT 1"));
    }

    private Payment requireSuccessfulPayment(Long orderId) {
        Payment payment = paymentMapper.selectOne(Wrappers.<Payment>lambdaQuery()
                .eq(Payment::getOrderId, orderId)
                .eq(Payment::getStatus, PaymentStatus.SUCCESS)
                .orderByDesc(Payment::getId)
                .last("LIMIT 1"));
        if (payment == null) {
            throw new BusinessException(PaymentErrorCode.PAY_ORDER_NOT_PAID);
        }
        return payment;
    }

    /**
     * 跨上下文调用方(order 的取消流程)传的是**字符串**而不是 {@code RefundReasonType}:
     * 跨上下文只能用对方的 service 包,不能引用 payment 的 domain 枚举(架构规则 L4)。
     */
    private static RefundReasonType parseReasonType(String name) {
        if (name == null || name.isBlank()) {
            return RefundReasonType.OTHER;
        }
        try {
            return RefundReasonType.valueOf(name);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(CommonErrorCode.COMMON_VALIDATION_FAILED,
                    "退款原因分类不合法",
                    List.of(new ErrorResponse.Detail("reasonType", "取值不在允许集合内")));
        }
    }

    private static boolean isPaid(String payStatus) {
        return PAY_STATUS_PAID.equals(payStatus) || PAY_STATUS_PARTIAL_REFUNDED.equals(payStatus);
    }

    private static PaymentView toView(Payment payment) {
        return new PaymentView(payment.getId(), payment.getOrderId(), payment.getOrderNo(),
                payment.getChannel() == null ? null : payment.getChannel().name(),
                payment.getStatus() == null ? null : payment.getStatus().name(),
                payment.getAmountCents() == null ? 0L : payment.getAmountCents(),
                payment.getTransactionId(), payment.getPrepayId(), payment.getPaidAt(), payment.getCreatedAt());
    }

    private static StartResult toStartResult(Payment payment, PaymentGateway.Prepay prepay) {
        return new StartResult(payment.getId(),
                payment.getChannel().name(),
                payment.getStatus().name(),
                payment.getAmountCents(),
                prepay.timeStamp(), prepay.nonceStr(), prepay.packageValue(), prepay.signType(), prepay.paySign(),
                // mockPayUrl 只在"渠道需要外部触发"时才有意义;当前 mock 通道发起即成功,所以恒为 null,
                // 不返回一个并不存在的回调地址
                null);
    }

    private void applyRefundSort(LambdaQueryWrapper<Refund> wrapper, SortSpec sortSpec) {
        switch (sortSpec.field()) {
            case "createdAt" -> wrapper.orderBy(true, sortSpec.ascending(), Refund::getCreatedAt);
            case "refundedAt" -> wrapper.orderBy(true, sortSpec.ascending(), Refund::getRefundedAt);
            case "amountCents" -> wrapper.orderBy(true, sortSpec.ascending(), Refund::getAmountCents);
            default -> throw new BusinessException(CommonErrorCode.COMMON_SORT_FIELD_NOT_ALLOWED);
        }
        wrapper.orderByDesc(Refund::getId);
    }
}
