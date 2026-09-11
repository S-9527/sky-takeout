package com.sky.order.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.sky.cart.service.CartService;
import com.sky.catalog.service.PurchasableItemView;
import com.sky.catalog.service.SetmealService;
import com.sky.common.domain.PageQuery;
import com.sky.common.domain.PageResponse;
import com.sky.common.domain.SortSpec;
import com.sky.common.error.BusinessException;
import com.sky.common.error.CommonErrorCode;
import com.sky.notification.service.OrderNotifier;
import com.sky.order.domain.CancelSide;
import com.sky.order.domain.ItemType;
import com.sky.order.domain.Order;
import com.sky.order.domain.OrderErrorCode;
import com.sky.order.domain.OrderItem;
import com.sky.order.domain.OrderStatus;
import com.sky.order.domain.PayStatus;
import com.sky.order.mapper.OrderItemCount;
import com.sky.order.mapper.OrderItemMapper;
import com.sky.insights.service.DateRangeValidator;
import com.sky.order.mapper.OrderMapper;
import com.sky.order.mapper.OrderStatusCount;
import com.sky.profile.service.AddressService;
import com.sky.profile.service.DeliveryAddressView;
import com.sky.shop.service.ShopStatusService;
import com.sky.testsupport.TableInfoTestSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderServiceTest {

    private static final long CUSTOMER = 7L;
    private static final long ADDRESS_ID = 501L;

    private final OrderMapper orderMapper = mock(OrderMapper.class);
    private final OrderItemMapper orderItemMapper = mock(OrderItemMapper.class);
    private final OrderNoGenerator orderNoGenerator = mock(OrderNoGenerator.class);
    private final CartService cartService = mock(CartService.class);
    private final AddressService addressService = mock(AddressService.class);
    private final ShopStatusService shopStatusService = mock(ShopStatusService.class);
    private final SetmealService setmealService = mock(SetmealService.class);
    private final OrderNotifier orderNotifier = mock(OrderNotifier.class);
    private final DateRangeValidator dateRangeValidator = mock(DateRangeValidator.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> valueOps = mock(ValueOperations.class);

    private final Map<String, String> redisValues = new HashMap<>();

    private final OrderService orderService = new OrderService(orderMapper, orderItemMapper, orderNoGenerator,
            cartService, addressService, shopStatusService, setmealService, orderNotifier, redis, dateRangeValidator);

    @BeforeAll
    static void registerTableInfo() {
        TableInfoTestSupport.register(Order.class, OrderItem.class);
    }

    @BeforeEach
    void setUpRedis() {
        redisValues.clear();
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenAnswer(invocation -> redisValues.get(invocation.getArgument(0)));
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            if (redisValues.containsKey(key)) {
                return false;
            }
            redisValues.put(key, invocation.getArgument(1));
            return true;
        });
        when(redis.delete(anyString())).thenAnswer(invocation -> redisValues.remove(invocation.getArgument(0)) != null);
        when(orderNoGenerator.next()).thenReturn("202501011200000001");
        when(shopStatusService.isOpen()).thenReturn(true);
    }

    // ---------------------------------------------------------------- 辅助

    private static CartService.CheckoutLine line(long id, ItemType type, Long dishId, Long setmealId,
                                                 int quantity, long price, List<CartService.FlavorChoiceRef> flavors) {
        PurchasableItemView goods = new PurchasableItemView(
                type == ItemType.DISH ? dishId : setmealId, type == ItemType.DISH ? "宫保鸡丁" : "单人川味套餐",
                "/files/x.jpg", price, type == ItemType.DISH ? 1L : 7L,
                type == ItemType.DISH ? "川湘菜" : "单人套餐", true, null);
        return new CartService.CheckoutLine(id, type.name(), dishId, setmealId, quantity, flavors, goods);
    }

    private static CartService.CheckoutLine dishLine() {
        return line(900L, ItemType.DISH, 101L, null, 2, 3800L,
                List.of(new CartService.FlavorChoiceRef("辣度", "微辣")));
    }

    private static CartService.CheckoutLine setmealLine() {
        return line(901L, ItemType.SETMEAL, null, 201L, 1, 4500L, List.of());
    }

    private static DeliveryAddressView address() {
        return new DeliveryAddressView(ADDRESS_ID, "张三", "13800138000", "北京市", "北京市", "朝阳区", "望京街道 1 号院");
    }

    private static Order order(long id, OrderStatus status) {
        Order order = new Order();
        order.setId(id);
        order.setCustomerId(CUSTOMER);
        order.setOrderNo("202501011200000001");
        order.setStatus(status);
        order.setPayStatus(PayStatus.UNPAID);
        return order;
    }

    private void cartHas(CartService.CheckoutLine... lines) {
        when(cartService.linesForCheckout(CUSTOMER)).thenReturn(List.of(lines));
    }

    private void addressIsValid() {
        when(addressService.findDeliveryAddress(CUSTOMER, ADDRESS_ID)).thenReturn(java.util.Optional.of(address()));
        when(addressService.findPreferredDeliveryAddress(CUSTOMER)).thenReturn(java.util.Optional.of(address()));
    }

    // ---------------------------------------------------------------- 试算

    @Test
    void previewRejectsEmptyCart() {
        when(cartService.linesForCheckout(CUSTOMER)).thenReturn(List.of());

        assertThatThrownBy(() -> orderService.preview(CUSTOMER, ADDRESS_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(OrderErrorCode.ORDER_CART_EMPTY));
    }

    @Test
    void previewComputesAmountsAndUsesProvidedAddress() {
        cartHas(dishLine(), setmealLine());
        addressIsValid();

        OrderService.PreviewResult preview = orderService.preview(CUSTOMER, ADDRESS_ID);

        // 3800 x 2 + 4500 = 12100,打包 200 + 配送 600
        assertThat(preview.amounts().totalAmountCents()).isEqualTo(12100L);
        assertThat(preview.amounts().packAmountCents()).isEqualTo(OrderService.PACK_AMOUNT_CENTS);
        assertThat(preview.amounts().deliveryAmountCents()).isEqualTo(OrderService.DELIVERY_AMOUNT_CENTS);
        assertThat(preview.amounts().discountAmountCents()).isZero();
        assertThat(preview.amounts().payAmountCents()).isEqualTo(12900L);
        assertThat(preview.items()).hasSize(2);
        assertThat(preview.items().get(0).name()).isEqualTo("宫保鸡丁");
        assertThat(preview.items().get(0).amountCents()).isEqualTo(7600L);
        assertThat(preview.address().id()).isEqualTo(ADDRESS_ID);
        assertThat(preview.shopOpen()).isTrue();
    }

    @Test
    void previewWithoutAddressIdFallsBackToPreferredAddress() {
        cartHas(dishLine());
        addressIsValid();

        assertThat(orderService.preview(CUSTOMER, null).address().id()).isEqualTo(ADDRESS_ID);
        verify(addressService).findPreferredDeliveryAddress(CUSTOMER);
    }

    @Test
    void previewRejectsInvalidAddress() {
        cartHas(dishLine());
        when(addressService.findDeliveryAddress(CUSTOMER, ADDRESS_ID)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> orderService.preview(CUSTOMER, ADDRESS_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(OrderErrorCode.ORDER_ADDRESS_INVALID));
    }

    @Test
    void previewRejectsUnavailableItem() {
        PurchasableItemView offSale = new PurchasableItemView(101L, "宫保鸡丁", null, 3800L, 1L, "川湘菜", false, "商品已停售");
        cartHas(new CartService.CheckoutLine(900L, "DISH", 101L, null, 1,
                List.of(), offSale));
        addressIsValid();

        assertThatThrownBy(() -> orderService.preview(CUSTOMER, ADDRESS_ID))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.errorCode()).isEqualTo(OrderErrorCode.ORDER_ITEM_NOT_ON_SALE);
                    assertThat(ex.details()).isNotEmpty();
                });
    }

    /** R1 只约束下单:打烊时试算照常返回,用 shopOpen=false 让前端禁用按钮。 */
    @Test
    void previewSucceedsWhenShopIsClosed() {
        cartHas(dishLine());
        addressIsValid();
        when(shopStatusService.isOpen()).thenReturn(false);

        assertThat(orderService.preview(CUSTOMER, ADDRESS_ID).shopOpen()).isFalse();
    }

    // ---------------------------------------------------------------- 下单

    @Test
    void submitPersistsOrderWithSnapshotsAndClearsCart() {
        cartHas(dishLine(), setmealLine());
        addressIsValid();
        when(setmealService.itemsOf(201L)).thenReturn(List.of(
                new SetmealService.ItemView(1L, 101L, "宫保鸡丁", 3800L, 1),
                new SetmealService.ItemView(2L, 112L, "米饭", 300L, 1)));
        when(orderMapper.insert(any(Order.class))).thenAnswer(invocation -> {
            ((Order) invocation.getArgument(0)).setId(4001L);
            return 1;
        });

        OrderService.SubmitResult result = orderService.submit(CUSTOMER, ADDRESS_ID, "不要香菜", 2, null);

        ArgumentCaptor<Order> saved = ArgumentCaptor.forClass(Order.class);
        verify(orderMapper).insert(saved.capture());
        Order order = saved.getValue();
        assertThat(order.getOrderNo()).isEqualTo("202501011200000001");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(order.getPayStatus()).isEqualTo(PayStatus.UNPAID);
        assertThat(order.getTotalAmountCents()).isEqualTo(12100L);
        assertThat(order.getPayAmountCents()).isEqualTo(12900L);
        assertThat(order.getConsignee()).isEqualTo("张三");
        assertThat(order.getSourceAddressId()).isEqualTo(ADDRESS_ID);
        assertThat(order.getTablewareCount()).isEqualTo(2);
        assertThat(order.getPlacedAt()).isNotNull();

        ArgumentCaptor<OrderItem> items = ArgumentCaptor.forClass(OrderItem.class);
        verify(orderItemMapper, times(2)).insert(items.capture());
        OrderItem dishItem = items.getAllValues().get(0);
        assertThat(dishItem.getNameSnapshot()).isEqualTo("宫保鸡丁");
        assertThat(dishItem.getAmountCents()).isEqualTo(7600L);
        assertThat(dishItem.getFlavorSnapshot()).extracting("name").containsExactly("辣度");
        assertThat(dishItem.getComboSnapshot()).isEmpty();

        OrderItem setmealItem = items.getAllValues().get(1);
        assertThat(setmealItem.getComboSnapshot()).extracting("name").containsExactly("宫保鸡丁", "米饭");
        assertThat(setmealItem.getFlavorSnapshot()).isEmpty();

        // R3:下单与清空购物车在同一事务里
        verify(cartService).clear(CUSTOMER);

        assertThat(result.id()).isEqualTo(4001L);
        assertThat(result.orderNo()).isEqualTo("202501011200000001");
        assertThat(result.payAmountCents()).isEqualTo(12900L);
        assertThat(result.needPay()).isTrue();
    }

    @Test
    void submitDefaultsTablewareToOne() {
        cartHas(dishLine());
        addressIsValid();
        when(orderMapper.insert(any(Order.class))).thenAnswer(invocation -> {
            ((Order) invocation.getArgument(0)).setId(4001L);
            return 1;
        });

        orderService.submit(CUSTOMER, ADDRESS_ID, null, null, null);

        ArgumentCaptor<Order> saved = ArgumentCaptor.forClass(Order.class);
        verify(orderMapper).insert(saved.capture());
        assertThat(saved.getValue().getTablewareCount()).isEqualTo(1);
    }

    @Test
    void submitRejectsClosedShop() {
        cartHas(dishLine());
        when(shopStatusService.isOpen()).thenReturn(false);

        assertThatThrownBy(() -> orderService.submit(CUSTOMER, ADDRESS_ID, null, 1, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(OrderErrorCode.ORDER_SHOP_CLOSED));
        verify(orderMapper, never()).insert(any(Order.class));
    }

    @Test
    void submitRejectsInvalidAddress() {
        cartHas(dishLine());
        when(addressService.findDeliveryAddress(CUSTOMER, ADDRESS_ID)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> orderService.submit(CUSTOMER, ADDRESS_ID, null, 1, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(OrderErrorCode.ORDER_ADDRESS_INVALID));
    }

    @Test
    void submitRejectsUnavailableItem() {
        PurchasableItemView offSale = new PurchasableItemView(101L, "宫保鸡丁", null, 3800L, 1L, "川湘菜", false, "商品已停售");
        cartHas(new CartService.CheckoutLine(900L, "DISH", 101L, null, 1, List.of(), offSale));
        addressIsValid();

        assertThatThrownBy(() -> orderService.submit(CUSTOMER, ADDRESS_ID, null, 1, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(OrderErrorCode.ORDER_ITEM_NOT_ON_SALE));
    }

    /** R5:请求体里的金额只用于价格变动检测,不参与计算。 */
    @Test
    void submitRejectsWhenClientTotalDoesNotMatchServerCalculation() {
        cartHas(dishLine());
        addressIsValid();

        assertThatThrownBy(() -> orderService.submit(CUSTOMER, ADDRESS_ID, null, 1, 9999L))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.errorCode()).isEqualTo(OrderErrorCode.ORDER_PRICE_CHANGED);
                    assertThat(ex.details()).isNotEmpty();
                });
        verify(orderMapper, never()).insert(any(Order.class));
    }

    @Test
    void submitAcceptsMatchingClientTotal() {
        cartHas(dishLine());
        addressIsValid();
        when(orderMapper.insert(any(Order.class))).thenAnswer(invocation -> {
            ((Order) invocation.getArgument(0)).setId(4001L);
            return 1;
        });

        orderService.submit(CUSTOMER, ADDRESS_ID, null, 1, 7600L);

        verify(orderMapper).insert(any(Order.class));
    }

    @Test
    void submitTwiceWithSameContentIsRejectedAsDuplicate() {
        cartHas(dishLine());
        addressIsValid();
        when(orderMapper.insert(any(Order.class))).thenAnswer(invocation -> {
            ((Order) invocation.getArgument(0)).setId(4001L);
            return 1;
        });

        orderService.submit(CUSTOMER, ADDRESS_ID, null, 1, null);

        assertThatThrownBy(() -> orderService.submit(CUSTOMER, ADDRESS_ID, null, 1, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(OrderErrorCode.ORDER_DUPLICATE_SUBMIT));
    }

    /** 校验失败要释放指纹,否则顾客改完地址/购物车会被锁 10 秒。 */
    @Test
    void failedSubmitReleasesTheDuplicateGuard() {
        cartHas(dishLine());
        when(shopStatusService.isOpen()).thenReturn(false);

        assertThatThrownBy(() -> orderService.submit(CUSTOMER, ADDRESS_ID, null, 1, null))
                .isInstanceOf(BusinessException.class);

        verify(redis).delete("sky:order:dup:" + CUSTOMER);
        assertThat(redisValues).doesNotContainKey("sky:order:dup:" + CUSTOMER);
    }

    @Test
    void submitWithDifferentContentWithinWindowIsAllowed() {
        cartHas(dishLine());
        addressIsValid();
        when(orderMapper.insert(any(Order.class))).thenAnswer(invocation -> {
            ((Order) invocation.getArgument(0)).setId(4001L);
            return 1;
        });

        orderService.submit(CUSTOMER, ADDRESS_ID, "第一次", 1, null);
        // 内容不同(备注变了)→ 覆盖指纹并放行
        orderService.submit(CUSTOMER, ADDRESS_ID, "第二次", 1, null);

        verify(orderMapper, times(2)).insert(any(Order.class));
    }

    // ---------------------------------------------------------------- 查询

    @Test
    void pageAppliesFiltersAndFillsItemCount() {
        Page<Order> page = new Page<>(1, 20);
        page.setRecords(List.of(order(4001L, OrderStatus.ACCEPTED)));
        page.setTotal(1);
        when(orderMapper.selectPage(any(), any())).thenReturn(page);
        OrderItemCount count = new OrderItemCount();
        count.setOrderId(4001L);
        count.setItemCount(3);
        when(orderItemMapper.countByOrderIds(any())).thenReturn(List.of(count));

        PageResponse<Order> result = orderService.page(CUSTOMER, OrderStatus.ACCEPTED,
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31), PageQuery.of(1, 20),
                new SortSpec("placedAt", true));

        assertThat(result.records()).hasSize(1);
        assertThat(result.records().get(0).getItemCount()).isEqualTo(3);
    }

    @Test
    void pageRejectsUnknownSortField() {
        assertThatThrownBy(() -> orderService.page(CUSTOMER, null, null, null, PageQuery.of(1, 20),
                new SortSpec("id", false)))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(CommonErrorCode.COMMON_SORT_FIELD_NOT_ALLOWED));
    }

    @Test
    void pageWithoutOrdersSkipsItemCountQuery() {
        Page<Order> page = new Page<>(1, 20);
        page.setRecords(List.of());
        page.setTotal(0);
        when(orderMapper.selectPage(any(), any())).thenReturn(page);

        assertThat(orderService.page(CUSTOMER, null, null, null, PageQuery.of(1, 20),
                new SortSpec("placedAt", true)).records()).isEmpty();
        verify(orderItemMapper, never()).countByOrderIds(any());
    }

    @Test
    void requireOwnedHidesOtherCustomersOrders() {
        Order other = order(4001L, OrderStatus.PENDING_PAYMENT);
        other.setCustomerId(999L);
        when(orderMapper.selectById(4001L)).thenReturn(other);

        assertThatThrownBy(() -> orderService.requireOwned(CUSTOMER, 4001L))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(OrderErrorCode.ORDER_NOT_FOUND));

        when(orderMapper.selectById(404L)).thenReturn(null);
        assertThatThrownBy(() -> orderService.requireOwned(CUSTOMER, 404L))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(OrderErrorCode.ORDER_NOT_FOUND));
    }

    @Test
    void itemsOfReturnsMapperResult() {
        when(orderItemMapper.selectList(any())).thenReturn(List.of(new OrderItem()));

        assertThat(orderService.itemsOf(4001L)).hasSize(1);
    }

    // ---------------------------------------------------------------- 取消

    @Test
    void customerCanCancelUnpaidOrder() {
        when(orderMapper.selectById(4001L)).thenReturn(order(4001L, OrderStatus.PENDING_PAYMENT));

        orderService.cancelByCustomer(CUSTOMER, 4001L, "不想要了");

        ArgumentCaptor<Order> update = ArgumentCaptor.forClass(Order.class);
        verify(orderMapper).updateById(update.capture());
        assertThat(update.getValue().getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(update.getValue().getCancelSide()).isEqualTo(CancelSide.CUSTOMER);
        assertThat(update.getValue().getCancelReason()).isEqualTo("不想要了");
        assertThat(update.getValue().getCancelledAt()).isNotNull();
    }

    /** 迁移合法但只有商家能做(需要退款流程,R6)→ ORDER_CANNOT_CANCEL。 */
    @Test
    void customerCannotCancelPaidOrderAwaitingMerchant() {
        when(orderMapper.selectById(4001L)).thenReturn(order(4001L, OrderStatus.PENDING_ACCEPTANCE));

        assertThatThrownBy(() -> orderService.cancelByCustomer(CUSTOMER, 4001L, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(OrderErrorCode.ORDER_CANNOT_CANCEL));
        verify(orderMapper, never()).updateById(any(Order.class));
    }

    /** 状态机不允许的迁移(已出餐/终态)→ ORDER_INVALID_TRANSITION。 */
    @Test
    void customerCannotCancelDeliveringOrTerminalOrders() {
        for (OrderStatus status : List.of(OrderStatus.DELIVERING, OrderStatus.COMPLETED, OrderStatus.CANCELLED)) {
            when(orderMapper.selectById(4001L)).thenReturn(order(4001L, status));
            assertThatThrownBy(() -> orderService.cancelByCustomer(CUSTOMER, 4001L, null))
                    .as("status=%s", status)
                    .isInstanceOfSatisfying(BusinessException.class,
                            ex -> assertThat(ex.errorCode()).isEqualTo(OrderErrorCode.ORDER_INVALID_TRANSITION));
        }
    }

    // ---------------------------------------------------------------- 再来一单

    @Test
    void reorderAddsItemsAndReportsSkippedOnes() {
        when(orderMapper.selectById(4001L)).thenReturn(order(4001L, OrderStatus.COMPLETED));
        OrderItem dishItem = new OrderItem();
        dishItem.setItemType(ItemType.DISH);
        dishItem.setDishId(101L);
        dishItem.setQuantity(2);
        dishItem.setNameSnapshot("宫保鸡丁");
        dishItem.setFlavorSnapshot(List.of(new com.sky.order.domain.FlavorChoice("辣度", "微辣")));
        OrderItem deletedItem = new OrderItem();
        deletedItem.setItemType(ItemType.SETMEAL);
        deletedItem.setNameSnapshot("已删除套餐");
        when(orderItemMapper.selectList(any())).thenReturn(List.of(dishItem, deletedItem));
        when(cartService.addOrSkip(eq(CUSTOMER), eq("DISH"), eq(101L), any(), eq(2), any())).thenReturn(null);

        OrderService.ReorderResult result = orderService.reorder(CUSTOMER, 4001L);

        assertThat(result.addedCount()).isEqualTo(1);
        assertThat(result.skippedItems()).hasSize(1);
        assertThat(result.skippedItems().get(0).name()).isEqualTo("已删除套餐");
        assertThat(result.skippedItems().get(0).reason()).isEqualTo("商品已不存在");
    }

    @Test
    void reorderReportsCartRejectionReason() {
        when(orderMapper.selectById(4001L)).thenReturn(order(4001L, OrderStatus.COMPLETED));
        OrderItem item = new OrderItem();
        item.setItemType(ItemType.DISH);
        item.setDishId(101L);
        item.setQuantity(1);
        item.setNameSnapshot("宫保鸡丁");
        when(orderItemMapper.selectList(any())).thenReturn(List.of(item));
        when(cartService.addOrSkip(any(), any(), any(), any(), any(), any())).thenReturn("商品已下架,无法加入购物车");

        OrderService.ReorderResult result = orderService.reorder(CUSTOMER, 4001L);

        assertThat(result.addedCount()).isZero();
        assertThat(result.skippedItems().get(0).reason()).isEqualTo("商品已下架,无法加入购物车");
    }

    @Test
    void reorderRejectsForeignOrder() {
        when(orderMapper.selectById(4001L)).thenReturn(order(4001L, OrderStatus.COMPLETED));
        when(orderMapper.selectById(404L)).thenReturn(null);

        assertThatThrownBy(() -> orderService.reorder(CUSTOMER, 404L))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(OrderErrorCode.ORDER_NOT_FOUND));
    }

    // ---------------------------------------------------------------- 催单

    @Test
    void remindNotifiesForInProgressOrders() {
        when(orderMapper.selectById(4001L)).thenReturn(order(4001L, OrderStatus.ACCEPTED));

        orderService.remind(CUSTOMER, 4001L, "请尽快派送");

        verify(orderNotifier).orderReminder(4001L, "202501011200000001", "请尽快派送");
        assertThat(redisValues).containsKey("sky:order:urge:4001");
    }

    @Test
    void remindRejectsOrdersThatCannotBeUrged() {
        for (OrderStatus status : List.of(OrderStatus.PENDING_PAYMENT, OrderStatus.COMPLETED, OrderStatus.CANCELLED)) {
            when(orderMapper.selectById(4001L)).thenReturn(order(4001L, status));
            assertThatThrownBy(() -> orderService.remind(CUSTOMER, 4001L, null))
                    .as("status=%s", status)
                    .isInstanceOfSatisfying(BusinessException.class,
                            ex -> assertThat(ex.errorCode()).isEqualTo(OrderErrorCode.ORDER_URGE_NOT_ALLOWED));
        }
        verify(orderNotifier, never()).orderReminder(any(), any(), any());
    }

    @Test
    void remindIsThrottledPerOrder() {
        when(orderMapper.selectById(4001L)).thenReturn(order(4001L, OrderStatus.DELIVERING));

        orderService.remind(CUSTOMER, 4001L, null);

        assertThatThrownBy(() -> orderService.remind(CUSTOMER, 4001L, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(OrderErrorCode.ORDER_URGE_TOO_FREQUENT));
        verify(orderNotifier, times(1)).orderReminder(any(), any(), any());
    }

    @Test
    void estimatedDeliveryAtIsDeliberatelyNull() {
        assertThat(OrderService.estimatedDeliveryAt()).isNull();
        assertThat(OrderService.SORT_WHITELIST).containsExactlyInAnyOrder("placedAt", "payAmountCents");
        assertThat(Set.of(OrderService.PACK_AMOUNT_CENTS, OrderService.DELIVERY_AMOUNT_CENTS)).isNotEmpty();
    }

    // ---------------------------------------------------------------- 管理端:查询、统计与状态迁移

    @Test
    void adminPageValidatesDateRangeThroughInsightsService() {
        Page<Order> page = new Page<>(1, 20);
        page.setRecords(List.of());
        page.setTotal(0);
        when(orderMapper.selectPage(any(), any())).thenReturn(page);

        orderService.pageForAdmin(OrderStatus.ACCEPTED, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31),
                "202501011200000001", "13800138000", CUSTOMER, PageQuery.of(1, 20),
                new SortSpec("placedAt", true));

        verify(dateRangeValidator).validate(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31));
        verify(orderMapper).selectPage(any(), any());
    }

    @Test
    void adminPagePropagatesDateRangeViolation() {
        org.mockito.Mockito.doThrow(new BusinessException(com.sky.insights.domain.InsightsErrorCode.REPORT_DATE_RANGE_INVALID))
                .when(dateRangeValidator).validate(any(), any());

        assertThatThrownBy(() -> orderService.pageForAdmin(null, LocalDate.of(2025, 1, 2), LocalDate.of(2025, 1, 1),
                null, null, null, PageQuery.of(1, 20), new SortSpec("placedAt", true)))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode())
                                .isEqualTo(com.sky.insights.domain.InsightsErrorCode.REPORT_DATE_RANGE_INVALID));
        verify(orderMapper, never()).selectPage(any(), any());
    }

    @Test
    void statusCountsFillsMissingStatesWithZero() {
        OrderStatusCount row = new OrderStatusCount();
        row.setStatus("ACCEPTED");
        row.setTotal(3L);
        OrderStatusCount unknown = new OrderStatusCount();
        unknown.setStatus("LEGACY_STATUS");
        unknown.setTotal(9L);
        when(orderMapper.countByStatus()).thenReturn(List.of(row, unknown));

        var counts = orderService.statusCounts();

        assertThat(counts.get(OrderStatus.ACCEPTED)).isEqualTo(3L);
        assertThat(counts.get(OrderStatus.PENDING_PAYMENT)).isZero();
        assertThat(counts.get(OrderStatus.COMPLETED)).isZero();
        assertThat(counts).hasSize(OrderStatus.values().length);
    }

    /** 统计查询失败按契约返回 500 ORDER_STATUS_COUNT_FAILED。 */
    @Test
    void statusCountsMapsQueryFailureToContractCode() {
        when(orderMapper.countByStatus()).thenThrow(new IllegalStateException("db down"));

        assertThatThrownBy(() -> orderService.statusCounts())
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(OrderErrorCode.ORDER_STATUS_COUNT_FAILED));
    }

    @Test
    void acceptMovesPendingAcceptanceToAccepted() {
        when(orderMapper.selectById(4001L)).thenReturn(order(4001L, OrderStatus.PENDING_ACCEPTANCE));

        orderService.accept(4001L);

        ArgumentCaptor<Order> update = ArgumentCaptor.forClass(Order.class);
        verify(orderMapper).updateById(update.capture());
        assertThat(update.getValue().getStatus()).isEqualTo(OrderStatus.ACCEPTED);
        assertThat(update.getValue().getAcceptedAt()).isNotNull();
    }

    @Test
    void acceptRejectsOrdersInOtherStates() {
        when(orderMapper.selectById(4001L)).thenReturn(order(4001L, OrderStatus.PENDING_PAYMENT));

        assertThatThrownBy(() -> orderService.accept(4001L))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(OrderErrorCode.ORDER_INVALID_TRANSITION));
    }

    @Test
    void startDeliveryAndCompleteFollowTheStateMachine() {
        when(orderMapper.selectById(4001L)).thenReturn(order(4001L, OrderStatus.ACCEPTED));
        orderService.startDelivery(4001L);
        ArgumentCaptor<Order> delivery = ArgumentCaptor.forClass(Order.class);
        verify(orderMapper).updateById(delivery.capture());
        assertThat(delivery.getValue().getStatus()).isEqualTo(OrderStatus.DELIVERING);
        assertThat(delivery.getValue().getDeliveringAt()).isNotNull();

        when(orderMapper.selectById(4002L)).thenReturn(order(4002L, OrderStatus.DELIVERING));
        orderService.complete(4002L);
        ArgumentCaptor<Order> completion = ArgumentCaptor.forClass(Order.class);
        verify(orderMapper, times(2)).updateById(completion.capture());
        assertThat(completion.getAllValues().get(1).getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(completion.getAllValues().get(1).getCompletedAt()).isNotNull();
    }

    /** R8:已完成是终态,不能再次迁移。 */
    @Test
    void completeRejectsTerminalOrders() {
        when(orderMapper.selectById(4001L)).thenReturn(order(4001L, OrderStatus.COMPLETED));

        assertThatThrownBy(() -> orderService.complete(4001L))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(OrderErrorCode.ORDER_INVALID_TRANSITION));
    }

    @Test
    void markCancelledWritesSideReasonAndTime() {
        orderService.markCancelled(4001L, CancelSide.MERCHANT, "菜品售完");

        ArgumentCaptor<Order> update = ArgumentCaptor.forClass(Order.class);
        verify(orderMapper).updateById(update.capture());
        assertThat(update.getValue().getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(update.getValue().getCancelSide()).isEqualTo(CancelSide.MERCHANT);
        assertThat(update.getValue().getCancelReason()).isEqualTo("菜品售完");
        assertThat(update.getValue().getCancelledAt()).isNotNull();
    }

    @Test
    void timeoutJobCancelsOnlyStillUnpaidOrders() {
        when(orderMapper.selectTimedOutIds(any(), anyInt())).thenReturn(List.of(4001L, 4002L, 4003L));
        when(orderMapper.selectById(4001L)).thenReturn(order(4001L, OrderStatus.PENDING_PAYMENT));
        // 4002 已经被支付(定时任务与支付回调竞争),4003 已经不存在
        when(orderMapper.selectById(4002L)).thenReturn(order(4002L, OrderStatus.PENDING_ACCEPTANCE));
        when(orderMapper.selectById(4003L)).thenReturn(null);

        int cancelled = orderService.cancelTimedOutOrders();

        assertThat(cancelled).isEqualTo(1);
        ArgumentCaptor<Order> update = ArgumentCaptor.forClass(Order.class);
        verify(orderMapper).updateById(update.capture());
        assertThat(update.getValue().getId()).isEqualTo(4001L);
        assertThat(update.getValue().getCancelSide()).isEqualTo(CancelSide.SYSTEM);
        assertThat(update.getValue().getCancelReason()).contains("超时");
        assertThat(OrderService.PAY_TIMEOUT_MINUTES).isEqualTo(15);
    }

    @Test
    void timeoutJobDoesNothingWhenNoTimedOutOrders() {
        when(orderMapper.selectTimedOutIds(any(), anyInt())).thenReturn(List.of());

        assertThat(orderService.cancelTimedOutOrders()).isZero();
        verify(orderMapper, never()).updateById(any(Order.class));
    }
}
