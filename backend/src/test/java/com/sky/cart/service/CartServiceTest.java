package com.sky.cart.service;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.sky.cart.domain.CartErrorCode;
import com.sky.cart.domain.CartItem;
import com.sky.cart.domain.FlavorChoice;
import com.sky.cart.domain.FlavorKeys;
import com.sky.cart.domain.ItemType;
import com.sky.cart.mapper.CartItemMapper;
import com.sky.catalog.service.DishService;
import com.sky.catalog.service.PurchasableItemView;
import com.sky.catalog.service.SetmealService;
import com.sky.common.error.BusinessException;
import com.sky.common.error.CommonErrorCode;
import com.sky.testsupport.TableInfoTestSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CartServiceTest {

    private static final long CUSTOMER = 7L;

    private final CartItemMapper cartItemMapper = mock(CartItemMapper.class);
    private final DishService dishService = mock(DishService.class);
    private final SetmealService setmealService = mock(SetmealService.class);

    private final CartService cartService = new CartService(cartItemMapper, dishService, setmealService);

    @BeforeAll
    static void registerTableInfo() {
        // summary/findExisting/clear 都要构 lambda wrapper,单测里没有 Spring 需要显式注册
        TableInfoTestSupport.register(CartItem.class);
    }

    // ---------------------------------------------------------------- 辅助

    private static PurchasableItemView dishView(long id, long price, long categoryId) {
        return new PurchasableItemView(id, "菜品" + id, "/files/dish/" + id + ".jpg", price,
                categoryId, "分类" + categoryId, true, null);
    }

    private static PurchasableItemView unavailableDishView(long id) {
        return new PurchasableItemView(id, "菜品" + id, null, 1000L, 1L, "分类1", false, "商品已停售");
    }

    private static PurchasableItemView setmealView(long id, long price) {
        return new PurchasableItemView(id, "套餐" + id, "/files/setmeal/" + id + ".jpg", price,
                7L, "单人套餐", true, null);
    }

    private static CartItem row(long id, ItemType type, Long dishId, Long setmealId, int quantity) {
        CartItem item = new CartItem();
        item.setId(id);
        item.setCustomerId(CUSTOMER);
        item.setItemType(type);
        item.setDishId(dishId);
        item.setSetmealId(setmealId);
        item.setQuantity(quantity);
        item.setFlavorKey("");
        return item;
    }

    private void goodsAre(PurchasableItemView... views) {
        Map<Long, PurchasableItemView> dishMap = new java.util.LinkedHashMap<>();
        Map<Long, PurchasableItemView> setmealMap = new java.util.LinkedHashMap<>();
        for (PurchasableItemView view : views) {
            if (view.categoryId() != null && view.categoryId() == 7L) {
                setmealMap.put(view.id(), view);
            } else {
                dishMap.put(view.id(), view);
            }
        }
        when(dishService.purchasableByIds(any())).thenReturn(dishMap);
        when(setmealService.purchasableByIds(any())).thenReturn(setmealMap);
    }

    // ---------------------------------------------------------------- 视图

    @Test
    void emptyCartReturnsEmptySummaryWithoutQueryingGoods() {
        when(cartItemMapper.selectList(any())).thenReturn(List.of());

        CartService.CartSummary summary = cartService.summary(CUSTOMER);

        assertThat(summary.groups()).isEmpty();
        assertThat(summary.totalQuantity()).isZero();
        assertThat(summary.totalAmountCents()).isZero();
        verify(dishService, never()).purchasableByIds(any());
    }

    /** D4:名称/图片/单价/小计全部来自联表实时值。 */
    @Test
    void summaryUsesLiveGoodsValuesAndGroupsByCategory() {
        when(cartItemMapper.selectList(any())).thenReturn(List.of(
                row(1L, ItemType.DISH, 101L, null, 2),
                row(2L, ItemType.DISH, 102L, null, 1),
                row(3L, ItemType.SETMEAL, null, 201L, 3)));
        goodsAre(dishView(101L, 3800L, 1L), dishView(102L, 5800L, 2L), setmealView(201L, 4500L));

        CartService.CartSummary summary = cartService.summary(CUSTOMER);

        assertThat(summary.groups()).hasSize(3);
        assertThat(summary.groups().get(0).categoryName()).isEqualTo("分类1");
        CartService.CartItemSnapshot first = summary.groups().get(0).items().get(0);
        assertThat(first.name()).isEqualTo("菜品101");
        assertThat(first.unitPriceCents()).isEqualTo(3800L);
        assertThat(first.amountCents()).isEqualTo(7600L);
        assertThat(first.available()).isTrue();
        assertThat(summary.totalQuantity()).isEqualTo(6);
        assertThat(summary.totalAmountCents()).isEqualTo(7600L + 5800L + 13500L);
        // 一次批量查询而不是每行一次
        verify(dishService, times(1)).purchasableByIds(any());
        verify(setmealService, times(1)).purchasableByIds(any());
    }

    @Test
    void summaryKeepsUnavailableRowsButMarksThem() {
        when(cartItemMapper.selectList(any())).thenReturn(List.of(row(1L, ItemType.DISH, 101L, null, 1)));
        goodsAre(unavailableDishView(101L));

        CartService.CartItemSnapshot item = cartService.summary(CUSTOMER).groups().get(0).items().get(0);

        assertThat(item.available()).isFalse();
        assertThat(item.unavailableReason()).isEqualTo("商品已停售");
        assertThat(item.amountCents()).isEqualTo(1000L);
    }

    // ---------------------------------------------------------------- 加购

    @Test
    void addCreatesNewRowAndReturnsLiveSnapshot() {
        when(dishService.purchasable(101L)).thenReturn(dishView(101L, 3800L, 1L));
        when(dishService.flavorOptions(101L)).thenReturn(Map.of());
        when(cartItemMapper.selectOne(any())).thenReturn(null);
        when(cartItemMapper.insert(any(CartItem.class))).thenAnswer(invocation -> {
            ((CartItem) invocation.getArgument(0)).setId(900L);
            return 1;
        });

        CartService.AddResult result = cartService.add(
                CUSTOMER, ItemType.DISH, 101L, null, 2, List.of());

        assertThat(result.created()).isTrue();
        assertThat(result.item().id()).isEqualTo(900L);
        assertThat(result.item().quantity()).isEqualTo(2);
        assertThat(result.item().unitPriceCents()).isEqualTo(3800L);
        assertThat(result.item().amountCents()).isEqualTo(7600L);

        ArgumentCaptor<CartItem> inserted = ArgumentCaptor.forClass(CartItem.class);
        verify(cartItemMapper).insert(inserted.capture());
        assertThat(inserted.getValue().getCustomerId()).isEqualTo(CUSTOMER);
        assertThat(inserted.getValue().getSetmealId()).isNull();
        assertThat(inserted.getValue().getFlavorKey()).isEmpty();
    }

    @Test
    void addMergesIntoExistingRow() {
        when(dishService.purchasable(101L)).thenReturn(dishView(101L, 3800L, 1L));
        when(dishService.flavorOptions(101L)).thenReturn(Map.of());
        when(cartItemMapper.selectOne(any())).thenReturn(row(900L, ItemType.DISH, 101L, null, 1));

        CartService.AddResult result = cartService.add(CUSTOMER, ItemType.DISH, 101L, null, 2, List.of());

        assertThat(result.created()).isFalse();
        assertThat(result.item().quantity()).isEqualTo(3);
        ArgumentCaptor<CartItem> update = ArgumentCaptor.forClass(CartItem.class);
        verify(cartItemMapper).updateById(update.capture());
        assertThat(update.getValue().getQuantity()).isEqualTo(3);
        verify(cartItemMapper, never()).insert(any(CartItem.class));
    }

    @Test
    void addRejectsMergeBeyondMaxQuantity() {
        when(dishService.purchasable(101L)).thenReturn(dishView(101L, 3800L, 1L));
        when(dishService.flavorOptions(101L)).thenReturn(Map.of());
        when(cartItemMapper.selectOne(any())).thenReturn(row(900L, ItemType.DISH, 101L, null, 98));

        assertThatThrownBy(() -> cartService.add(CUSTOMER, ItemType.DISH, 101L, null, 2, List.of()))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(CartErrorCode.CART_QUANTITY_INVALID));
        verify(cartItemMapper, never()).updateById(any(CartItem.class));
    }

    /** 并发加购同一行:唯一键冲突后退化成合并,而不是把 500 抛给顾客。 */
    @Test
    void addFallsBackToMergeOnDuplicateKey() {
        when(dishService.purchasable(101L)).thenReturn(dishView(101L, 3800L, 1L));
        when(dishService.flavorOptions(101L)).thenReturn(Map.of());
        when(cartItemMapper.selectOne(any())).thenReturn(null, row(900L, ItemType.DISH, 101L, null, 4));
        when(cartItemMapper.insert(any(CartItem.class))).thenThrow(new DuplicateKeyException("uk_cart_item_identity"));

        CartService.AddResult result = cartService.add(CUSTOMER, ItemType.DISH, 101L, null, 1, List.of());

        assertThat(result.created()).isFalse();
        assertThat(result.item().quantity()).isEqualTo(5);
    }

    @Test
    void addReportsConflictWhenDuplicateKeyRowIsStillMissing() {
        when(dishService.purchasable(101L)).thenReturn(dishView(101L, 3800L, 1L));
        when(dishService.flavorOptions(101L)).thenReturn(Map.of());
        when(cartItemMapper.selectOne(any())).thenReturn(null);
        when(cartItemMapper.insert(any(CartItem.class))).thenThrow(new DuplicateKeyException("uk_cart_item_identity"));

        assertThatThrownBy(() -> cartService.add(CUSTOMER, ItemType.DISH, 101L, null, 1, List.of()))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(CommonErrorCode.COMMON_CONFLICT));
    }

    @Test
    void addRejectsUnavailableGoodsWithReason() {
        when(dishService.purchasable(101L)).thenReturn(unavailableDishView(101L));

        assertThatThrownBy(() -> cartService.add(CUSTOMER, ItemType.DISH, 101L, null, 1, List.of()))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.errorCode()).isEqualTo(CartErrorCode.CART_ITEM_OFF_SALE);
                    assertThat(ex.details()).isNotEmpty();
                });
        verify(cartItemMapper, never()).insert(any(CartItem.class));
    }

    @Test
    void addPropagatesCatalogNotFoundForUnknownGoods() {
        when(dishService.purchasable(999L))
                .thenThrow(new BusinessException(com.sky.catalog.domain.CatalogErrorCode.DISH_NOT_FOUND));

        assertThatThrownBy(() -> cartService.add(CUSTOMER, ItemType.DISH, 999L, null, 1, List.of()))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode())
                                .isEqualTo(com.sky.catalog.domain.CatalogErrorCode.DISH_NOT_FOUND));
    }

    @Test
    void addSetmealUsesSetmealLookup() {
        when(setmealService.purchasable(201L)).thenReturn(setmealView(201L, 4500L));
        when(cartItemMapper.selectOne(any())).thenReturn(null);
        when(cartItemMapper.insert(any(CartItem.class))).thenAnswer(invocation -> {
            ((CartItem) invocation.getArgument(0)).setId(901L);
            return 1;
        });

        CartService.AddResult result = cartService.add(CUSTOMER, ItemType.SETMEAL, null, 201L, 1, List.of());

        assertThat(result.item().name()).isEqualTo("套餐201");
        ArgumentCaptor<CartItem> inserted = ArgumentCaptor.forClass(CartItem.class);
        verify(cartItemMapper).insert(inserted.capture());
        assertThat(inserted.getValue().getDishId()).isNull();
        assertThat(inserted.getValue().getSetmealId()).isEqualTo(201L);
    }

    @Test
    void addRejectsMismatchedItemReference() {
        List<Runnable> invalid = List.of(
                () -> cartService.add(CUSTOMER, ItemType.DISH, 101L, 201L, 1, List.of()),
                () -> cartService.add(CUSTOMER, ItemType.DISH, null, null, 1, List.of()),
                () -> cartService.add(CUSTOMER, ItemType.SETMEAL, 101L, null, 1, List.of()),
                () -> cartService.add(CUSTOMER, ItemType.SETMEAL, 101L, 201L, 1, List.of()),
                () -> cartService.add(CUSTOMER, null, 101L, null, 1, List.of()));

        for (Runnable call : invalid) {
            assertThatThrownBy(call::run)
                    .isInstanceOfSatisfying(BusinessException.class,
                            ex -> assertThat(ex.errorCode()).isEqualTo(CommonErrorCode.COMMON_VALIDATION_FAILED));
        }
    }

    @Test
    void addRejectsIllegalQuantity() {
        List<Integer> invalid = java.util.Arrays.asList(null, 0, -1, 100);

        for (Integer quantity : invalid) {
            assertThatThrownBy(() -> cartService.add(CUSTOMER, ItemType.DISH, 101L, null, quantity, List.of()))
                    .as("quantity=%s", quantity)
                    .isInstanceOfSatisfying(BusinessException.class,
                            ex -> assertThat(ex.errorCode()).isEqualTo(CartErrorCode.CART_QUANTITY_INVALID));
        }
    }

    // ---------------------------------------------------------------- 口味校验

    private void dishWithFlavors() {
        when(dishService.purchasable(101L)).thenReturn(dishView(101L, 3800L, 1L));
        when(dishService.flavorOptions(101L)).thenReturn(Map.of(
                "辣度", List.of("不辣", "微辣", "中辣"),
                "忌口", List.of("不要葱", "无")));
    }

    @Test
    void addRequiresFlavorWhenDishHasConfiguration() {
        dishWithFlavors();

        assertThatThrownBy(() -> cartService.add(CUSTOMER, ItemType.DISH, 101L, null, 1, List.of()))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(CartErrorCode.CART_FLAVOR_REQUIRED));
    }

    @Test
    void addRequiresEveryFlavorDimension() {
        dishWithFlavors();

        assertThatThrownBy(() -> cartService.add(CUSTOMER, ItemType.DISH, 101L, null, 1,
                List.of(new FlavorChoice("辣度", "微辣"))))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(CartErrorCode.CART_FLAVOR_REQUIRED));
    }

    @Test
    void addRejectsUnknownOptionOrDimension() {
        dishWithFlavors();

        assertThatThrownBy(() -> cartService.add(CUSTOMER, ItemType.DISH, 101L, null, 1,
                List.of(new FlavorChoice("辣度", "变态辣"), new FlavorChoice("忌口", "无"))))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(CartErrorCode.CART_FLAVOR_INVALID));

        assertThatThrownBy(() -> cartService.add(CUSTOMER, ItemType.DISH, 101L, null, 1,
                List.of(new FlavorChoice("甜度", "少糖"), new FlavorChoice("忌口", "无"))))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(CartErrorCode.CART_FLAVOR_INVALID));
    }

    @Test
    void addRejectsDuplicatedOrBlankFlavorDimension() {
        dishWithFlavors();

        assertThatThrownBy(() -> cartService.add(CUSTOMER, ItemType.DISH, 101L, null, 1,
                List.of(new FlavorChoice("辣度", "微辣"), new FlavorChoice("辣度", "中辣"))))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(CartErrorCode.CART_FLAVOR_INVALID));

        assertThatThrownBy(() -> cartService.add(CUSTOMER, ItemType.DISH, 101L, null, 1,
                List.of(new FlavorChoice("  ", "微辣"))))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(CartErrorCode.CART_FLAVOR_INVALID));
    }

    @Test
    void addStoresFlavorChoiceAndKeyForValidSelection() {
        dishWithFlavors();
        when(cartItemMapper.selectOne(any())).thenReturn(null);
        when(cartItemMapper.insert(any(CartItem.class))).thenAnswer(invocation -> {
            ((CartItem) invocation.getArgument(0)).setId(902L);
            return 1;
        });
        List<FlavorChoice> choice = List.of(new FlavorChoice("辣度", "微辣"), new FlavorChoice("忌口", "无"));

        cartService.add(CUSTOMER, ItemType.DISH, 101L, null, 1, choice);

        ArgumentCaptor<CartItem> inserted = ArgumentCaptor.forClass(CartItem.class);
        verify(cartItemMapper).insert(inserted.capture());
        assertThat(inserted.getValue().getFlavorChoice()).isEqualTo(choice);
        assertThat(inserted.getValue().getFlavorKey()).isEqualTo(FlavorKeys.of(choice));
    }

    /** 套餐没有口味配置,提交口味就是"配置里没有的东西"。 */
    @Test
    void addRejectsFlavorForSetmeal() {
        when(setmealService.purchasable(201L)).thenReturn(setmealView(201L, 4500L));

        assertThatThrownBy(() -> cartService.add(CUSTOMER, ItemType.SETMEAL, null, 201L, 1,
                List.of(new FlavorChoice("辣度", "微辣"))))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(CartErrorCode.CART_FLAVOR_INVALID));
    }

    // ---------------------------------------------------------------- 改量与清空

    @Test
    void updateQuantityOverwritesValueAndReturnsLiveSnapshot() {
        when(cartItemMapper.selectById(900L)).thenReturn(row(900L, ItemType.DISH, 101L, null, 1));
        goodsAre(dishView(101L, 3800L, 1L));

        Optional<CartService.CartItemSnapshot> result = cartService.updateQuantity(CUSTOMER, 900L, 5);

        assertThat(result).isPresent();
        assertThat(result.get().quantity()).isEqualTo(5);
        assertThat(result.get().amountCents()).isEqualTo(19000L);
        ArgumentCaptor<CartItem> update = ArgumentCaptor.forClass(CartItem.class);
        verify(cartItemMapper).updateById(update.capture());
        assertThat(update.getValue().getQuantity()).isEqualTo(5);
    }

    @Test
    void updateQuantityZeroDeletesTheRow() {
        when(cartItemMapper.selectById(900L)).thenReturn(row(900L, ItemType.DISH, 101L, null, 1));

        Optional<CartService.CartItemSnapshot> result = cartService.updateQuantity(CUSTOMER, 900L, 0);

        assertThat(result).isEmpty();
        verify(cartItemMapper).deleteById(900L);
        verify(cartItemMapper, never()).updateById(any(CartItem.class));
    }

    @Test
    void updateQuantityRejectsOutOfRange() {
        for (Integer quantity : List.of(-1, 100)) {
            assertThatThrownBy(() -> cartService.updateQuantity(CUSTOMER, 900L, quantity))
                    .as("quantity=%s", quantity)
                    .isInstanceOfSatisfying(BusinessException.class,
                            ex -> assertThat(ex.errorCode()).isEqualTo(CartErrorCode.CART_QUANTITY_INVALID));
        }
    }

    /** R9:他人的购物车行一律 404,不泄露存在性。 */
    @Test
    void updateQuantityRejectsOtherCustomersRow() {
        CartItem other = row(900L, ItemType.DISH, 101L, null, 1);
        other.setCustomerId(999L);
        when(cartItemMapper.selectById(900L)).thenReturn(other);

        assertThatThrownBy(() -> cartService.updateQuantity(CUSTOMER, 900L, 2))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(CartErrorCode.CART_ITEM_NOT_FOUND));
    }

    @Test
    void updateQuantityRejectsMissingRow() {
        when(cartItemMapper.selectById(404L)).thenReturn(null);

        assertThatThrownBy(() -> cartService.updateQuantity(CUSTOMER, 404L, 2))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(CartErrorCode.CART_ITEM_NOT_FOUND));
    }

    @Test
    void clearDeletesOnlyCurrentCustomersRows() {
        cartService.clear(CUSTOMER);

        verify(cartItemMapper).delete(any());
    }

    @Test
    void addAcceptsDishWithFlavorsWhenSelectionIsComplete() {
        dishWithFlavors();
        when(cartItemMapper.selectOne(any())).thenReturn(row(900L, ItemType.DISH, 101L, null, 1));

        CartService.AddResult result = cartService.add(CUSTOMER, ItemType.DISH, 101L, null, 1,
                List.of(new FlavorChoice("辣度", "微辣"), new FlavorChoice("忌口", "无")));

        assertThat(result.created()).isFalse();
        verify(dishService, times(1)).flavorOptions(101L);
        verify(cartItemMapper, never()).insert(any(CartItem.class));
    }
}
