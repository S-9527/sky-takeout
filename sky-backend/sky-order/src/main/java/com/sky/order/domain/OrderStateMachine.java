package com.sky.order.domain;

import com.sky.constant.MessageConstant;
import com.sky.order.entity.Orders;
import com.sky.order.enumeration.OrderStatus;
import com.sky.order.event.OrderStatusChangedEvent;
import com.sky.order.exception.OrderBusinessException;
import com.sky.order.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 订单状态机：集中维护订单状态迁移规则，统一校验迁移合法性并落库。
 */
@Component
@RequiredArgsConstructor
public class OrderStateMachine {

    private final OrderMapper orderMapper;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 允许的状态迁移表：from -> 可达的 to 集合
     */
    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS =
            new EnumMap<>(OrderStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(OrderStatus.PENDING_PAYMENT,
                EnumSet.of(OrderStatus.TO_BE_CONFIRMED, OrderStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(OrderStatus.TO_BE_CONFIRMED,
                EnumSet.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(OrderStatus.CONFIRMED,
                EnumSet.of(OrderStatus.DELIVERY_IN_PROGRESS, OrderStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(OrderStatus.DELIVERY_IN_PROGRESS,
                EnumSet.of(OrderStatus.COMPLETED, OrderStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(OrderStatus.COMPLETED, EnumSet.noneOf(OrderStatus.class));
        ALLOWED_TRANSITIONS.put(OrderStatus.CANCELLED, EnumSet.noneOf(OrderStatus.class));
    }

    /**
     * 状态迁移：校验合法性、设置新状态、填充附加字段并落库
     *
     * @param order  已加载的待变更订单
     * @param target 目标状态
     * @param audit  附加字段填充（取消原因、取消时间等）
     * @return 迁移后的订单
     */
    public Orders transition(Orders order, OrderStatus target, Consumer<Orders> audit) {
        return transition(order, target, audit, null);
    }

    /**
     * 状态迁移：校验合法性、设置新状态、填充附加字段并落库，随后发布状态变更事件
     *
     * @param order  已加载的待变更订单
     * @param target 目标状态
     * @param audit  附加字段填充（取消原因、取消时间等）
     * @param cause  迁移原因描述
     * @return 迁移后的订单
     */
    public Orders transition(Orders order, OrderStatus target, Consumer<Orders> audit, String cause) {
        if (order == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        OrderStatus from = OrderStatus.fromCode(order.getStatus());
        if (from == null || from == target) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        Set<OrderStatus> allowed = ALLOWED_TRANSITIONS.get(from);
        if (CollectionUtils.isEmpty(allowed) || !allowed.contains(target)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        order.setStatus(target.getCode());
        if (audit != null) {
            audit.accept(order);
        }
        orderMapper.updateById(order);

        eventPublisher.publishEvent(new OrderStatusChangedEvent(
                order.getId(), order.getNumber(), from, target, cause));
        return order;
    }

    /**
     * 是否允许从 from 迁移到 target
     */
    public boolean canTransition(OrderStatus from, OrderStatus target) {
        if (from == null || target == null) {
            return false;
        }
        Set<OrderStatus> allowed = ALLOWED_TRANSITIONS.get(from);
        return allowed != null && allowed.contains(target);
    }
}