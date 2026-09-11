package com.sky.order.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import com.sky.cart.service.CartService;
import com.sky.common.domain.PageResponse;
import com.sky.common.error.BusinessException;
import com.sky.common.error.CommonErrorCode;
import com.sky.order.api.dto.CancelOrderRequest;
import com.sky.order.api.dto.OrderDetailResponse;
import com.sky.order.api.dto.OrderPreviewRequest;
import com.sky.order.api.dto.OrderPreviewResponse;
import com.sky.order.api.dto.OrderRemindRequest;
import com.sky.order.api.dto.OrderSubmitRequest;
import com.sky.order.api.dto.OrderSubmitResultResponse;
import com.sky.order.api.dto.ReorderResultResponse;
import com.sky.order.domain.FlavorChoice;
import com.sky.order.domain.ItemType;
import com.sky.order.domain.Order;
import com.sky.order.domain.OrderItem;
import com.sky.order.domain.OrderStatus;
import com.sky.order.domain.PayStatus;
import com.sky.order.service.OrderService;
import com.sky.payment.service.PaymentService;
import com.sky.profile.service.DeliveryAddressView;
import com.sky.security.Audience;
import com.sky.security.CurrentPrincipal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderControllerTest {

    private static final long CUSTOMER = 7L;

    private final OrderService orderService = mock(OrderService.class);
    private final PaymentService paymentService = mock(PaymentService.class);
    private final OrderController controller = new OrderController(orderService, paymentService);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private static void actingAsCustomer() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new CurrentPrincipal(CUSTOMER, Audience.CUSTOMER, "CUSTOMER"), null));
    }

    private static Order order() {
        Order order = new Order();
        order.setId(4001L);
        order.setCustomerId(CUSTOMER);
        order.setOrderNo("202501011200000001");
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setPayStatus(PayStatus.UNPAID);
        order.setTotalAmountCents(7600L);
        order.setPackAmountCents(200L);
        order.setDeliveryAmountCents(600L);
        order.setDiscountAmountCents(0L);
        order.setPayAmountCents(8400L);
        order.setConsignee("张三");
        order.setTablewareCount(1);
        return order;
    }

    @Test
    void previewFlattensAmountsAndMapsItems() {
        actingAsCustomer();
        when(orderService.preview(CUSTOMER, 501L)).thenReturn(new OrderService.PreviewResult(
                new OrderService.Amounts(7600L, 200L, 600L, 0L, 8400L),
                List.of(new OrderService.PreviewLine("DISH", 101L, null, "宫保鸡丁", "/files/x.jpg",
                        3800L, 2, 7600L, List.of(new CartService.FlavorChoiceRef("辣度", "微辣")))),
                new DeliveryAddressView(501L, "张三", "13800138000", "北京市", "北京市", "朝阳区", "望京街道 1 号院"),
                true));

        OrderPreviewResponse response = controller.preview(new OrderPreviewRequest(501L));

        assertThat(response.totalAmountCents()).isEqualTo(7600L);
        assertThat(response.payAmountCents()).isEqualTo(8400L);
        assertThat(response.shopOpen()).isTrue();
        assertThat(response.estimatedDeliveryAt()).isNull();
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).name()).isEqualTo("宫保鸡丁");
        assertThat(response.address().id()).isEqualTo(501L);
    }

    @Test
    void previewToleratesNullBody() {
        actingAsCustomer();
        when(orderService.preview(CUSTOMER, null)).thenReturn(new OrderService.PreviewResult(
                new OrderService.Amounts(0L, 200L, 600L, 0L, 800L), List.of(),
                new DeliveryAddressView(501L, "张三", "1", "北京", "北京", "朝阳", "x"), false));

        assertThat(controller.preview(null).shopOpen()).isFalse();
        verify(orderService).preview(CUSTOMER, null);
    }

    @Test
    void submitMapsResult() {
        actingAsCustomer();
        when(orderService.submit(CUSTOMER, 501L, "不要香菜", 2, 7600L)).thenReturn(
                new OrderService.SubmitResult(4001L, "202501011200000001", OrderStatus.PENDING_PAYMENT, 8400L, true));

        OrderSubmitResultResponse response = controller.submit(new OrderSubmitRequest(501L, "不要香菜", 2, 7600L));

        assertThat(response.id()).isEqualTo(4001L);
        assertThat(response.status()).isEqualTo("PENDING_PAYMENT");
        assertThat(response.payAmountCents()).isEqualTo(8400L);
        assertThat(response.needPay()).isTrue();
    }

    @Test
    void pageMapsOrdersAndRejectsUnknownSort() {
        actingAsCustomer();
        when(orderService.page(eq(CUSTOMER), any(), any(), any(), any(), any()))
                .thenReturn(PageResponse.of(List.of(order()), 1, 20, 1));

        PageResponse<?> response = controller.page(1, 20, "placedAt,desc", OrderStatus.PENDING_PAYMENT, null, null);

        assertThat(response.records()).hasSize(1);

        assertThatThrownBy(() -> controller.page(1, 20, "id,asc", null, null, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(CommonErrorCode.COMMON_SORT_FIELD_NOT_ALLOWED));
    }

    @Test
    void getByIdMapsDetailWithSnapshotItems() {
        actingAsCustomer();
        when(orderService.requireOwned(CUSTOMER, 4001L)).thenReturn(order());
        OrderItem item = new OrderItem();
        item.setId(8001L);
        item.setItemType(ItemType.DISH);
        item.setNameSnapshot("宫保鸡丁");
        item.setUnitPriceCents(3800L);
        item.setQuantity(2);
        item.setAmountCents(7600L);
        item.setFlavorSnapshot(List.of(new FlavorChoice("辣度", "微辣")));
        when(orderService.itemsOf(4001L)).thenReturn(List.of(item));

        OrderDetailResponse response = controller.getById(4001L);

        assertThat(response.id()).isEqualTo(4001L);
        assertThat(response.status()).isEqualTo("PENDING_PAYMENT");
        assertThat(response.payStatus()).isEqualTo("UNPAID");
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).nameSnapshot()).isEqualTo("宫保鸡丁");
        assertThat(response.items().get(0).flavorSnapshot()).hasSize(1);
        // 菜品行没有套餐组成快照
        assertThat(response.items().get(0).comboSnapshot()).isNull();
    }

    @Test
    void cancelPassesReasonAndToleratesNullBody() {
        actingAsCustomer();

        controller.cancel(4001L, new CancelOrderRequest("不想要了"));
        controller.cancel(4001L, null);

        verify(orderService).cancelByCustomer(CUSTOMER, 4001L, "不想要了");
        verify(orderService).cancelByCustomer(CUSTOMER, 4001L, null);
    }

    @Test
    void reorderMapsSkippedItems() {
        actingAsCustomer();
        when(orderService.reorder(CUSTOMER, 4001L)).thenReturn(new OrderService.ReorderResult(
                1, List.of(new OrderService.SkippedItem("下架菜", "商品已停售"))));

        ReorderResultResponse response = controller.reorder(4001L);

        assertThat(response.addedCount()).isEqualTo(1);
        assertThat(response.skippedItems()).hasSize(1);
        assertThat(response.skippedItems().get(0).name()).isEqualTo("下架菜");
        assertThat(response.skippedItems().get(0).reason()).isEqualTo("商品已停售");
    }

    @Test
    void remindPassesMessageAndToleratesNullBody() {
        actingAsCustomer();

        controller.remind(4001L, new OrderRemindRequest("请尽快派送"));
        controller.remind(4001L, null);

        verify(orderService).remind(CUSTOMER, 4001L, "请尽快派送");
        verify(orderService).remind(CUSTOMER, 4001L, null);
        verify(orderService, org.mockito.Mockito.times(0)).requireOwned(anyLong(), anyLong());
    }
}
