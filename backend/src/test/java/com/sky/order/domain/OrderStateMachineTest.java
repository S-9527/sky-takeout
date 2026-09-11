package com.sky.order.domain;

import org.junit.jupiter.api.Test;

import java.util.Set;

import com.sky.common.error.BusinessException;
import com.sky.common.error.ErrorCode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 状态机是领域文档 §4 的唯一实现,所以这里把**全部 36 种组合**都钉住:
 * 以后新增状态或放宽迁移,必然有一条断言先红。
 */
class OrderStateMachineTest {

    /** 领域文档 §4「合法迁移」表,逐条抄写。 */
    private static final Set<String> LEGAL = Set.of(
            "PENDING_PAYMENT->PENDING_ACCEPTANCE",
            "PENDING_PAYMENT->CANCELLED",
            "PENDING_ACCEPTANCE->ACCEPTED",
            "PENDING_ACCEPTANCE->CANCELLED",
            "ACCEPTED->DELIVERING",
            "ACCEPTED->CANCELLED",
            "DELIVERING->COMPLETED");

    @Test
    void everyCombinationMatchesTheDocumentedTable() {
        for (OrderStatus from : OrderStatus.values()) {
            for (OrderStatus to : OrderStatus.values()) {
                boolean expected = LEGAL.contains(from + "->" + to);
                assertThat(OrderStateMachine.canTransition(from, to))
                        .as("%s → %s", from, to)
                        .isEqualTo(expected);
            }
        }
        assertThat(LEGAL).hasSize(7);
    }

    @Test
    void illegalTransitionIsRejectedWithTheContractCode() {
        assertThatThrownBy(() -> OrderStateMachine.requireTransition(OrderStatus.DELIVERING, OrderStatus.CANCELLED))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.errorCode()).isEqualTo(OrderErrorCode.ORDER_INVALID_TRANSITION);
                    assertThat(ex.details()).isNotEmpty();
                });
    }

    @Test
    void legalTransitionPasses() {
        OrderStateMachine.requireTransition(OrderStatus.PENDING_PAYMENT, OrderStatus.PENDING_ACCEPTANCE);
        OrderStateMachine.requireTransition(OrderStatus.ACCEPTED, OrderStatus.DELIVERING);
    }

    /** 终态不可再迁移——包括"取消已取消的订单"这种看起来无害的操作。 */
    @Test
    void terminalStatesCannotMoveAnywhere() {
        for (OrderStatus terminal : new OrderStatus[]{OrderStatus.COMPLETED, OrderStatus.CANCELLED}) {
            assertThat(OrderStateMachine.allowedTargets(terminal)).isEmpty();
            for (OrderStatus to : OrderStatus.values()) {
                assertThat(OrderStateMachine.canTransition(terminal, to)).as("%s → %s", terminal, to).isFalse();
            }
        }
    }

    /** 不得跳级:PENDING_PAYMENT 不能直接到 ACCEPTED / DELIVERING / COMPLETED。 */
    @Test
    void noSkippingLevels() {
        assertThat(OrderStateMachine.allowedTargets(OrderStatus.PENDING_PAYMENT))
                .containsExactlyInAnyOrder(OrderStatus.PENDING_ACCEPTANCE, OrderStatus.CANCELLED);
        assertThat(OrderStateMachine.allowedTargets(OrderStatus.ACCEPTED))
                .containsExactlyInAnyOrder(OrderStatus.DELIVERING, OrderStatus.CANCELLED);
    }

    @Test
    void nullsAreNotTransitions() {
        assertThat(OrderStateMachine.canTransition(null, OrderStatus.ACCEPTED)).isFalse();
        assertThat(OrderStateMachine.canTransition(OrderStatus.ACCEPTED, null)).isFalse();
    }

    @Test
    void statusFlagsReadNaturally() {
        assertThat(OrderStatus.COMPLETED.isTerminal()).isTrue();
        assertThat(OrderStatus.CANCELLED.isTerminal()).isTrue();
        assertThat(OrderStatus.DELIVERING.isTerminal()).isFalse();
        assertThat(OrderStatus.PENDING_PAYMENT.isUnpaid()).isTrue();
        assertThat(OrderStatus.ACCEPTED.isUnpaid()).isFalse();
    }

    @Test
    void stateEnumValuesMatchTheContract() {
        assertThat(OrderStatus.values()).extracting(Enum::name).containsExactly(
                "PENDING_PAYMENT", "PENDING_ACCEPTANCE", "ACCEPTED", "DELIVERING", "COMPLETED", "CANCELLED");
        assertThat(OrderStatus.PENDING_PAYMENT.getValue()).isEqualTo("PENDING_PAYMENT");
    }

    @Test
    void errorCodesCarryStatusAndMessage() {
        ErrorCode code = OrderErrorCode.ORDER_URGE_TOO_FREQUENT;
        assertThat(code.code()).isEqualTo("ORDER_URGE_TOO_FREQUENT");
        assertThat(code.httpStatus().value()).isEqualTo(409);
        assertThat(code.defaultMessage()).isNotBlank();

        assertThat(OrderErrorCode.ORDER_CART_EMPTY.httpStatus().value()).isEqualTo(422);
        assertThat(OrderErrorCode.ORDER_NOT_FOUND.httpStatus().value()).isEqualTo(404);
        assertThat(OrderErrorCode.ORDER_STATUS_COUNT_FAILED.httpStatus().value()).isEqualTo(500);
    }
}
