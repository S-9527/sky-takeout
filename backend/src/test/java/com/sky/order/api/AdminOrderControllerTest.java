package com.sky.order.api;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.sky.common.domain.PageResponse;
import com.sky.common.error.BusinessException;
import com.sky.common.error.CommonErrorCode;
import com.sky.order.api.dto.CancelOrderRequest;
import com.sky.order.api.dto.OrderStatusCountsResponse;
import com.sky.order.api.dto.RejectOrderRequest;
import com.sky.order.domain.ItemType;
import com.sky.order.domain.Order;
import com.sky.order.domain.OrderItem;
import com.sky.order.domain.OrderStatus;
import com.sky.order.domain.PayStatus;
import com.sky.order.service.OrderCancellationService;
import com.sky.order.service.OrderService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminOrderControllerTest {

    private final OrderService orderService = mock(OrderService.class);
    private final OrderCancellationService cancellationService = mock(OrderCancellationService.class);
    private final AdminOrderController controller = new AdminOrderController(orderService, cancellationService);

    private static Order order(long id, OrderStatus status) {
        Order order = new Order();
        order.setId(id);
        order.setOrderNo("202501011200000001");
        order.setStatus(status);
        order.setPayStatus(PayStatus.PAID);
        order.setPayAmountCents(5200L);
        return order;
    }

    @Test
    void pageMapsOrdersAndRejectsUnknownSort() {
        when(orderService.pageForAdmin(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(PageResponse.of(List.of(order(4001L, OrderStatus.ACCEPTED)), 1, 20, 1));

        PageResponse<?> response = controller.page(1, 20, "placedAt,desc", OrderStatus.ACCEPTED,
                null, null, "202501011200000001", "13800138000", 7L);

        assertThat(response.records()).hasSize(1);

        assertThatThrownBy(() -> controller.page(1, 20, "id,asc", null, null, null, null, null, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(CommonErrorCode.COMMON_SORT_FIELD_NOT_ALLOWED));
    }

    @Test
    void statusCountsFlattensEnumMap() {
        Map<OrderStatus, Long> counts = new EnumMap<>(OrderStatus.class);
        for (OrderStatus status : OrderStatus.values()) {
            counts.put(status, 0L);
        }
        counts.put(OrderStatus.PENDING_ACCEPTANCE, 5L);
        counts.put(OrderStatus.COMPLETED, 120L);
        when(orderService.statusCounts()).thenReturn(counts);

        OrderStatusCountsResponse response = controller.statusCounts();

        assertThat(response.pendingAcceptance()).isEqualTo(5L);
        assertThat(response.completed()).isEqualTo(120L);
        assertThat(response.cancelled()).isZero();
        assertThat(response.all()).isEqualTo(125L);
    }

    @Test
    void detailReturnsSnapshots() {
        when(orderService.requireById(4001L)).thenReturn(order(4001L, OrderStatus.ACCEPTED));
        OrderItem item = new OrderItem();
        item.setItemType(ItemType.SETMEAL);
        item.setNameSnapshot("单人川味套餐");
        item.setAmountCents(4500L);
        item.setQuantity(1);
        when(orderService.itemsOf(4001L)).thenReturn(List.of(item));

        var response = controller.getById(4001L);

        assertThat(response.id()).isEqualTo(4001L);
        assertThat(response.status()).isEqualTo("ACCEPTED");
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).itemType()).isEqualTo("SETMEAL");
    }

    @Test
    void transitionsDelegateToServices() {
        controller.accept(4001L);
        verify(orderService).accept(4001L);

        controller.startDelivery(4001L);
        verify(orderService).startDelivery(4001L);

        controller.complete(4001L);
        verify(orderService).complete(4001L);
    }

    @Test
    void rejectPassesReasonToCancellationService() {
        controller.reject(4001L, new RejectOrderRequest("菜品售完"));

        verify(cancellationService).reject(4001L, "菜品售完");
    }

    @Test
    void cancelPassesReasonAndToleratesNullReason() {
        controller.cancel(4001L, new CancelOrderRequest("顾客电话取消"));
        controller.cancel(4001L, new CancelOrderRequest(null));

        verify(cancellationService).cancelByMerchant(4001L, "顾客电话取消");
        verify(cancellationService).cancelByMerchant(eq(4001L), eq(null));
    }
}
