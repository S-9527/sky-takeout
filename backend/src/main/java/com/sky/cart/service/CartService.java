package com.sky.cart.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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
import com.sky.common.error.ErrorResponse;

/**
 * 购物车:加购合并、覆盖式改量、清空与分组视图。
 *
 * <p>三条硬规则:
 * <ul>
 *   <li><b>R2</b> 加购的商品必须处于可售状态(起售 + 所属分类启用);</li>
 *   <li><b>R9</b> 只能操作本人的购物车行,他人 id 一律 404(不泄露存在性);</li>
 *   <li><b>D4</b> 名称/图片/价格一律联表取实时值,购物车里没有任何快照。</li>
 * </ul>
 * 商品信息通过 catalog 的 service 读取({@link PurchasableItemView}),不引用对方的实体——
 * 架构规则 L4 要求跨上下文只能经由对方 service 包中的类型。
 */
@Service
public class CartService {

    /** 单行数量上限。合并后超过即 422 {@code CART_QUANTITY_INVALID}。 */
    public static final int MAX_QUANTITY = 99;

    /** 商品缺失时的兜底文案(正常情况下不可能:外键 CASCADE 会连购物车行一起删)。 */
    private static final String GOODS_MISSING_REASON = "商品已下架";

    private final CartItemMapper cartItemMapper;
    private final DishService dishService;
    private final SetmealService setmealService;

    public CartService(CartItemMapper cartItemMapper, DishService dishService, SetmealService setmealService) {
        this.cartItemMapper = cartItemMapper;
        this.dishService = dishService;
        this.setmealService = setmealService;
    }

    /** 购物车行(含实时商品信息)。 */
    public record CartItemSnapshot(
            Long id,
            ItemType itemType,
            Long dishId,
            Long setmealId,
            String name,
            String imageUrl,
            Long unitPriceCents,
            Integer quantity,
            Long amountCents,
            List<FlavorChoice> flavorChoice,
            boolean available,
            String unavailableReason) {
    }

    /** 按分类分组。 */
    public record CartGroup(Long categoryId, String categoryName, List<CartItemSnapshot> items) {
    }

    /** 购物车全量视图。 */
    public record CartSummary(List<CartGroup> groups, int totalQuantity, long totalAmountCents) {
    }

    public record AddResult(CartItemSnapshot item, boolean created) {
    }

    /** 跨上下文的口味入参(order 不能引用 cart 的 {@code FlavorChoice})。 */
    public record FlavorChoiceRef(String name, String option) {
    }

    /** 结算用的购物车行:实时商品视图 + 数量 + 口味。 */
    public record CheckoutLine(
            Long id,
            String itemType,
            Long dishId,
            Long setmealId,
            Integer quantity,
            List<FlavorChoiceRef> flavorChoice,
            PurchasableItemView goods) {
    }

    // ---------------------------------------------------------------- 跨上下文:结算与再来一单

    /**
     * 结算用的购物车行(含**不可售**的行:下单方要据此报 422,而不是自己过滤掉)。
     *
     * <p>返回的 {@code CheckoutLine} 定义在 service 包,商品信息是 catalog 的 service 层类型,
     * 两者都是"跨上下文允许依赖的形状"(架构规则 L4)。
     */
    public List<CheckoutLine> linesForCheckout(Long customerId) {
        List<CartItem> rows = cartItemMapper.selectList(Wrappers.<CartItem>lambdaQuery()
                .eq(CartItem::getCustomerId, customerId)
                .orderByAsc(CartItem::getId));
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, PurchasableItemView> goods = loadGoods(rows);
        return rows.stream()
                .map(row -> new CheckoutLine(
                        row.getId(),
                        row.getItemType() == null ? null : row.getItemType().name(),
                        row.getDishId(),
                        row.getSetmealId(),
                        row.getQuantity(),
                        row.getFlavorChoice().stream()
                                .map(choice -> new FlavorChoiceRef(choice.name(), choice.option()))
                                .toList(),
                        goods.get(goodsIdOf(row))))
                .toList();
    }

    /**
     * "再来一单"用:加购一件商品,失败不抛异常而是返回**跳过原因**。
     *
     * <p>历史订单里的商品可能已下架/已删除,甚至数量合并后超限;这些都不该让整单重来失败,
     * 契约要求把它们列进 {@code skippedItems}。
     */
    public String addOrSkip(Long customerId, String itemType, Long dishId, Long setmealId,
                            Integer quantity, List<FlavorChoiceRef> flavorChoice) {
        try {
            add(customerId, ItemType.valueOf(itemType), dishId, setmealId, quantity,
                    flavorChoice == null ? List.of() : flavorChoice.stream()
                            .map(ref -> new FlavorChoice(ref.name(), ref.option()))
                            .toList());
            return null;
        } catch (BusinessException ex) {
            return ex.getMessage();
        }
    }

    // ---------------------------------------------------------------- 查询

    /**
     * 购物车视图:按商品所属分类分组,金额与商品信息都是**实时值**。
     *
     * <p>不可售的行仍然返回(前端要展示"已下架"并让顾客删掉),但标记 {@code available=false}。
     */
    public CartSummary summary(Long customerId) {
        List<CartItem> rows = cartItemMapper.selectList(Wrappers.<CartItem>lambdaQuery()
                .eq(CartItem::getCustomerId, customerId)
                .orderByAsc(CartItem::getId));
        if (rows.isEmpty()) {
            return new CartSummary(List.of(), 0, 0L);
        }

        Map<Long, PurchasableItemView> goods = loadGoods(rows);

        Map<Long, List<CartItemSnapshot>> byCategory = new LinkedHashMap<>();
        Map<Long, String> categoryNames = new LinkedHashMap<>();
        int totalQuantity = 0;
        long totalAmountCents = 0L;
        for (CartItem row : rows) {
            PurchasableItemView item = goods.get(goodsIdOf(row));
            CartItemSnapshot snapshot = toSnapshot(row, item);
            Long categoryId = item == null ? null : item.categoryId();
            byCategory.computeIfAbsent(categoryId, key -> new ArrayList<>()).add(snapshot);
            if (item != null && item.categoryId() != null) {
                categoryNames.putIfAbsent(item.categoryId(), item.categoryName());
            }
            totalQuantity += row.getQuantity();
            totalAmountCents += snapshot.amountCents();
        }

        List<CartGroup> groups = byCategory.entrySet().stream()
                .map(entry -> new CartGroup(entry.getKey(),
                        entry.getKey() == null ? "已下架商品" : categoryNames.get(entry.getKey()),
                        List.copyOf(entry.getValue())))
                .sorted(Comparator.comparing(CartGroup::categoryId,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        return new CartSummary(groups, totalQuantity, totalAmountCents);
    }

    // ---------------------------------------------------------------- 加购

    /**
     * 加入购物车。同菜同口味合并数量。
     *
     * @return {@code created=true} 表示新建了一行(HTTP 201),否则合并到了已有行(HTTP 200)
     */
    @Transactional
    public AddResult add(Long customerId, ItemType itemType, Long dishId, Long setmealId,
                         Integer quantity, List<FlavorChoice> flavorChoice) {
        requireItemReference(itemType, dishId, setmealId);
        requireQuantity(quantity, false);

        PurchasableItemView goods = itemType == ItemType.DISH
                ? dishService.purchasable(dishId)
                : setmealService.purchasable(setmealId);
        requirePurchasable(goods);
        requireValidFlavor(itemType == ItemType.DISH ? dishService.flavorOptions(dishId) : Map.of(), flavorChoice);

        String flavorKey = FlavorKeys.of(flavorChoice);
        CartItem existing = findExisting(customerId, itemType, dishId, setmealId, flavorKey);
        if (existing != null) {
            return new AddResult(mergeInto(existing, quantity, goods), false);
        }

        CartItem item = new CartItem();
        item.setCustomerId(customerId);
        item.setItemType(itemType);
        item.setDishId(itemType == ItemType.DISH ? dishId : null);
        item.setSetmealId(itemType == ItemType.SETMEAL ? setmealId : null);
        item.setQuantity(quantity);
        item.setFlavorChoice(flavorChoice);
        item.setFlavorKey(flavorKey);
        try {
            cartItemMapper.insert(item);
        } catch (DuplicateKeyException ex) {
            // 并发加购同一行:唯一键(含生成列)兜底,退化成合并;再由事务内的再查确认一次
            CartItem concurrent = findExisting(customerId, itemType, dishId, setmealId, flavorKey);
            if (concurrent == null) {
                throw new BusinessException(CommonErrorCode.COMMON_CONFLICT);
            }
            return new AddResult(mergeInto(concurrent, quantity, goods), false);
        }
        return new AddResult(toSnapshot(item, goods), true);
    }

    // ---------------------------------------------------------------- 改量与清空

    /**
     * 覆盖式改量。
     *
     * @param quantity 最终数量;{@code 0} 表示删除该行
     * @return 更新后的行;删除时为 {@link Optional#empty()}
     */
    @Transactional
    public Optional<CartItemSnapshot> updateQuantity(Long customerId, Long itemId, Integer quantity) {
        requireQuantity(quantity, true);
        CartItem item = requireOwned(customerId, itemId);
        if (quantity == 0) {
            cartItemMapper.deleteById(itemId);
            return Optional.empty();
        }
        if (quantity > MAX_QUANTITY) {
            throw new BusinessException(CartErrorCode.CART_QUANTITY_INVALID,
                    "数量不合理,请重新输入",
                    List.of(new ErrorResponse.Detail("quantity", "单行最多 " + MAX_QUANTITY + " 件")));
        }
        CartItem update = new CartItem();
        update.setId(itemId);
        update.setQuantity(quantity);
        cartItemMapper.updateById(update);

        // 直接用"已知的新数量"构造返回行,不再回查一次(库里就是刚写进去的值)
        PurchasableItemView goods = loadGoods(List.of(item)).get(goodsIdOf(item));
        CartItem updatedRow = new CartItem();
        updatedRow.setId(itemId);
        updatedRow.setCustomerId(item.getCustomerId());
        updatedRow.setItemType(item.getItemType());
        updatedRow.setDishId(item.getDishId());
        updatedRow.setSetmealId(item.getSetmealId());
        updatedRow.setQuantity(quantity);
        updatedRow.setFlavorChoiceJson(item.getFlavorChoiceJson());
        updatedRow.setFlavorKey(item.getFlavorKey());
        return Optional.of(toSnapshot(updatedRow, goods));
    }

    /** 清空本人购物车。幂等:空购物车同样返回成功。 */
    @Transactional
    public void clear(Long customerId) {
        cartItemMapper.delete(Wrappers.<CartItem>lambdaQuery().eq(CartItem::getCustomerId, customerId));
    }

    // ---------------------------------------------------------------- 内部

    private CartItemSnapshot mergeInto(CartItem existing, int delta, PurchasableItemView goods) {
        int merged = existing.getQuantity() + delta;
        if (merged > MAX_QUANTITY) {
            throw new BusinessException(CartErrorCode.CART_QUANTITY_INVALID,
                    "数量不合理,请重新输入",
                    List.of(new ErrorResponse.Detail("quantity", "单行最多 " + MAX_QUANTITY + " 件")));
        }
        CartItem update = new CartItem();
        update.setId(existing.getId());
        update.setQuantity(merged);
        cartItemMapper.updateById(update);

        CartItem mergedRow = new CartItem();
        mergedRow.setId(existing.getId());
        mergedRow.setCustomerId(existing.getCustomerId());
        mergedRow.setItemType(existing.getItemType());
        mergedRow.setDishId(existing.getDishId());
        mergedRow.setSetmealId(existing.getSetmealId());
        mergedRow.setQuantity(merged);
        mergedRow.setFlavorChoiceJson(existing.getFlavorChoiceJson());
        mergedRow.setFlavorKey(existing.getFlavorKey());
        return toSnapshot(mergedRow, goods);
    }

    private CartItem requireOwned(Long customerId, Long itemId) {
        CartItem item = cartItemMapper.selectById(itemId);
        if (item == null || !customerId.equals(item.getCustomerId())) {
            throw new BusinessException(CartErrorCode.CART_ITEM_NOT_FOUND);
        }
        return item;
    }

    private CartItem findExisting(Long customerId, ItemType itemType, Long dishId, Long setmealId, String flavorKey) {
        LambdaQueryWrapper<CartItem> wrapper = Wrappers.<CartItem>lambdaQuery()
                .eq(CartItem::getCustomerId, customerId)
                .eq(CartItem::getItemType, itemType)
                .eq(CartItem::getFlavorKey, flavorKey);
        // 另一个引用列必须是 NULL:唯一键建在 IFNULL(x,0) 的生成列上,查询条件也要对齐
        if (itemType == ItemType.DISH) {
            wrapper.eq(CartItem::getDishId, dishId).isNull(CartItem::getSetmealId);
        } else {
            wrapper.eq(CartItem::getSetmealId, setmealId).isNull(CartItem::getDishId);
        }
        return cartItemMapper.selectOne(wrapper);
    }

    private Map<Long, PurchasableItemView> loadGoods(List<CartItem> rows) {
        Set<Long> dishIds = rows.stream()
                .filter(row -> row.getItemType() == ItemType.DISH)
                .map(CartItem::getDishId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> setmealIds = rows.stream()
                .filter(row -> row.getItemType() == ItemType.SETMEAL)
                .map(CartItem::getSetmealId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, PurchasableItemView> goods = new LinkedHashMap<>(dishService.purchasableByIds(dishIds));
        goods.putAll(setmealService.purchasableByIds(setmealIds));
        return goods;
    }

    private CartItemSnapshot toSnapshot(CartItem row, PurchasableItemView item) {
        long unitPrice = item == null || item.priceCents() == null ? 0L : item.priceCents();
        return new CartItemSnapshot(
                row.getId(),
                row.getItemType(),
                row.getDishId(),
                row.getSetmealId(),
                item == null ? null : item.name(),
                item == null ? null : item.imageUrl(),
                item == null ? null : item.priceCents(),
                row.getQuantity(),
                unitPrice * row.getQuantity(),
                row.getFlavorChoice(),
                item != null && item.available(),
                item == null ? GOODS_MISSING_REASON : item.unavailableReason());
    }

    private static Long goodsIdOf(CartItem row) {
        return row.getItemType() == ItemType.DISH ? row.getDishId() : row.getSetmealId();
    }

    private static void requireItemReference(ItemType itemType, Long dishId, Long setmealId) {
        if (itemType == null) {
            throw new BusinessException(CommonErrorCode.COMMON_VALIDATION_FAILED,
                    "itemType 不能为空",
                    List.of(new ErrorResponse.Detail("itemType", "只能是 DISH 或 SETMEAL")));
        }
        boolean valid = itemType == ItemType.DISH
                ? dishId != null && setmealId == null
                : setmealId != null && dishId == null;
        if (!valid) {
            throw new BusinessException(CommonErrorCode.COMMON_VALIDATION_FAILED,
                    "itemType 与商品 id 不匹配",
                    List.of(new ErrorResponse.Detail("itemType",
                            itemType == ItemType.DISH ? "DISH 时必须且只能传 dishId" : "SETMEAL 时必须且只能传 setmealId")));
        }
    }

    /** @param allowZero 改量接口允许 0(=删除);加购要求至少 1 */
    private static void requireQuantity(Integer quantity, boolean allowZero) {
        int min = allowZero ? 0 : 1;
        if (quantity == null || quantity < min || quantity > MAX_QUANTITY) {
            throw new BusinessException(CartErrorCode.CART_QUANTITY_INVALID,
                    "数量不合理,请重新输入",
                    List.of(new ErrorResponse.Detail("quantity", min + "~" + MAX_QUANTITY + " 之间的整数")));
        }
    }

    private static void requirePurchasable(PurchasableItemView goods) {
        if (!goods.available()) {
            throw new BusinessException(CartErrorCode.CART_ITEM_OFF_SALE,
                    "商品已下架,无法加入购物车",
                    List.of(new ErrorResponse.Detail("itemId", goods.unavailableReason())));
        }
    }

    /**
     * 口味校验。
     *
     * @param config 该商品的口味配置(维度 → 选项);套餐为空 Map
     */
    private static void requireValidFlavor(Map<String, List<String>> config, List<FlavorChoice> flavorChoice) {
        List<FlavorChoice> choices = flavorChoice == null ? List.of() : flavorChoice;
        for (FlavorChoice choice : choices) {
            if (choice == null || choice.name() == null || choice.name().isBlank()
                    || choice.option() == null || choice.option().isBlank()) {
                throw new BusinessException(CartErrorCode.CART_FLAVOR_INVALID);
            }
        }
        if (config.isEmpty()) {
            // 没有口味配置的商品不需要选;选了就是"配置里没有的东西"
            if (!choices.isEmpty()) {
                throw new BusinessException(CartErrorCode.CART_FLAVOR_INVALID);
            }
            return;
        }

        Map<String, String> chosen = new LinkedHashMap<>();
        for (FlavorChoice choice : choices) {
            if (chosen.putIfAbsent(choice.name(), choice.option()) != null) {
                throw new BusinessException(CartErrorCode.CART_FLAVOR_INVALID,
                        "所选口味已变更,请重新选择",
                        List.of(new ErrorResponse.Detail("flavorChoice", "口味维度「" + choice.name() + "」重复")));
            }
            List<String> options = config.get(choice.name());
            if (options == null || !options.contains(choice.option())) {
                throw new BusinessException(CartErrorCode.CART_FLAVOR_INVALID,
                        "所选口味已变更,请重新选择",
                        List.of(new ErrorResponse.Detail("flavorChoice",
                                "「" + choice.name() + "」没有选项「" + choice.option() + "」")));
            }
        }
        // 每个维度都必须选:少选一个,菜品就少了一维信息,下单时无法还原顾客意图
        if (!chosen.keySet().equals(config.keySet())) {
            throw new BusinessException(CartErrorCode.CART_FLAVOR_REQUIRED,
                    "请选择商品口味",
                    List.of(new ErrorResponse.Detail("flavorChoice", "需要选择全部口味维度")));
        }
    }
}
