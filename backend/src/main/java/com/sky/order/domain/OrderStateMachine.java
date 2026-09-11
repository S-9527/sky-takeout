package com.sky.order.domain;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import com.sky.common.error.BusinessException;
import com.sky.common.error.ErrorResponse;

/**
 * 订单状态机(领域文档 §4 的**唯一实现**)。
 *
 * <p>旧实现把 {@code if (status == 2) ... else if (status == 3)} 散落在各 service,状态图只存在于
 * 作者脑中,新增状态必然漏改。这里把允许的迁移写成一张表,R10 由它强制:非法迁移抛
 * {@code ORDER_INVALID_TRANSITION},DB 不做约束。
 *
 * <pre>
 *   PENDING_PAYMENT ──支付成功──→ PENDING_ACCEPTANCE ──商家接单──→ ACCEPTED ──开始派送──→ DELIVERING ──确认送达──→ COMPLETED
 *        │                              │                          │
 *        └──顾客/系统取消──→ CANCELLED ←─┴──商家拒单/取消───────────┘
 * </pre>
 *
 * <p>明确不允许:{@code DELIVERING → CANCELLED}(已出餐,只能走售后);终态不可再迁移;不得跳级。
 */
public final class OrderStateMachine {

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = new EnumMap<>(OrderStatus.class);

    static {
        ALLOWED.put(OrderStatus.PENDING_PAYMENT, EnumSet.of(OrderStatus.PENDING_ACCEPTANCE, OrderStatus.CANCELLED));
        ALLOWED.put(OrderStatus.PENDING_ACCEPTANCE, EnumSet.of(OrderStatus.ACCEPTED, OrderStatus.CANCELLED));
        ALLOWED.put(OrderStatus.ACCEPTED, EnumSet.of(OrderStatus.DELIVERING, OrderStatus.CANCELLED));
        ALLOWED.put(OrderStatus.DELIVERING, EnumSet.of(OrderStatus.COMPLETED));
        ALLOWED.put(OrderStatus.COMPLETED, EnumSet.noneOf(OrderStatus.class));
        ALLOWED.put(OrderStatus.CANCELLED, EnumSet.noneOf(OrderStatus.class));
    }

    private OrderStateMachine() {
    }

    public static boolean canTransition(OrderStatus from, OrderStatus to) {
        if (from == null || to == null) {
            return false;
        }
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    /** 合法则通过,否则抛 422 {@code ORDER_INVALID_TRANSITION}。 */
    public static void requireTransition(OrderStatus from, OrderStatus to) {
        if (!canTransition(from, to)) {
            throw new BusinessException(OrderErrorCode.ORDER_INVALID_TRANSITION,
                    "订单当前状态不允许该操作",
                    java.util.List.of(new ErrorResponse.Detail("status", from + " → " + to + " 不是合法迁移")));
        }
    }

    /** 该状态下允许到达的目标集合(供文档/调试与测试断言用)。 */
    public static Set<OrderStatus> allowedTargets(OrderStatus from) {
        return Set.copyOf(ALLOWED.getOrDefault(from, Set.of()));
    }
}
