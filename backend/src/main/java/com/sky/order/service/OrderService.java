package com.sky.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.sky.cart.service.CartService;
import com.sky.common.domain.PageQuery;
import com.sky.common.domain.PageResponse;
import com.sky.common.domain.SortSpec;
import com.sky.common.error.BusinessException;
import com.sky.common.error.CommonErrorCode;
import com.sky.common.error.ErrorResponse;
import com.sky.common.util.Hashes;
import com.sky.common.util.Times;
import com.sky.order.domain.CancelSide;
import com.sky.order.domain.ComboSnapshotItem;
import com.sky.order.domain.FlavorChoice;
import com.sky.order.domain.ItemType;
import com.sky.order.domain.Order;
import com.sky.order.domain.OrderErrorCode;
import com.sky.order.domain.OrderItem;
import com.sky.order.domain.OrderStateMachine;
import com.sky.order.domain.OrderStatus;
import com.sky.order.domain.PayMethod;
import com.sky.order.domain.PayStatus;
import com.sky.order.mapper.OrderItemCount;
import com.sky.order.mapper.OrderItemMapper;
import com.sky.order.mapper.OrderMapper;
import com.sky.notification.service.OrderNotifier;
import com.sky.profile.service.AddressService;
import com.sky.profile.service.DeliveryAddressView;
import com.sky.catalog.service.SetmealService;
import com.sky.shop.service.ShopStatusService;

/**
 * 订单:试算、下单、查询、取消、再来一单、催单。
 *
 * <p>这一层是 R3/R4/R5/R9/R10 的落点:
 * <ul>
 *   <li><b>R5</b> 金额一律由服务端按**当前库价**重算,请求体里的金额只用于"价格变动检测";</li>
 *   <li><b>R3</b> 下单 = 校验 + 快照 + 清空购物车 + 生成待付款单,全程同一事务;</li>
 *   <li><b>R4</b> 购物车非空、商品仍可售、地址属于本人,逐条校验;</li>
 *   <li><b>R9</b> 顾客侧只认自己的订单,他人订单一律 404;</li>
 *   <li><b>R10</b> 状态迁移只能经 {@link OrderStateMachine}。</li>
 * </ul>
 */
@Service
public class OrderService {

    /**
     * 打包费:每单固定 2.00 元。
     *
     * <p>领域文档没有规定金额,这是 {@code docs/03-api.md} §2.4「阈值与自主拍板项」里的选择:
     * 固定单价而不是按件计费,避免一次改动牵动下单/退款/统计三处口径。
     */
    public static final long PACK_AMOUNT_CENTS = 200L;

    /** 配送费:每单固定 6.00 元(同上,属于自主拍板项)。 */
    public static final long DELIVERY_AMOUNT_CENTS = 600L;

    /** 与 openapi 的 {@code OrderSubmitRequest.tablewareCount} 上限一致。 */
    public static final int MAX_TABLEWARE_COUNT = 20;

    /** 顾客端订单列表排序白名单(与 openapi 的 pageMyOrders 描述一致)。 */
    public static final Set<String> SORT_WHITELIST = Set.of("placedAt", "payAmountCents");

    /** 重复提交防护窗口:10 秒内内容一致的提交只允许一次(契约 §2.4 第 4 项)。 */
    static final Duration DUPLICATE_SUBMIT_WINDOW = Duration.ofSeconds(10);

    /** 催单节流:同订单 5 分钟一次(契约 §2.4 第 5 项)。 */
    static final Duration URGE_WINDOW = Duration.ofMinutes(5);

    private static final String DUP_KEY_PREFIX = "sky:order:dup:";
    private static final String URGE_KEY_PREFIX = "sky:order:urge:";

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderNoGenerator orderNoGenerator;
    private final CartService cartService;
    private final AddressService addressService;
    private final ShopStatusService shopStatusService;
    private final SetmealService setmealService;
    private final OrderNotifier orderNotifier;
    private final StringRedisTemplate redis;

    public OrderService(OrderMapper orderMapper, OrderItemMapper orderItemMapper, OrderNoGenerator orderNoGenerator,
                        CartService cartService, AddressService addressService, ShopStatusService shopStatusService,
                        SetmealService setmealService, OrderNotifier orderNotifier, StringRedisTemplate redis) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.orderNoGenerator = orderNoGenerator;
        this.cartService = cartService;
        this.addressService = addressService;
        this.shopStatusService = shopStatusService;
        this.setmealService = setmealService;
        this.orderNotifier = orderNotifier;
        this.redis = redis;
    }

    /** 金额构成。{@code pay = total + pack + delivery - discount}。 */
    public record Amounts(long totalAmountCents, long packAmountCents, long deliveryAmountCents,
                          long discountAmountCents, long payAmountCents) {
    }

    /** 试算明细(实时值,不是快照)。 */
    public record PreviewLine(String itemType, Long dishId, Long setmealId, String name, String imageUrl,
                              Long unitPriceCents, Integer quantity, long amountCents,
                              List<CartService.FlavorChoiceRef> flavorChoice) {
    }

    public record PreviewResult(Amounts amounts, List<PreviewLine> items, DeliveryAddressView address,
                                boolean shopOpen) {
    }

    public record SubmitResult(Long id, String orderNo, OrderStatus status, long payAmountCents, boolean needPay) {
    }

    public record SkippedItem(String name, String reason) {
    }

    public record ReorderResult(int addedCount, List<SkippedItem> skippedItems) {
    }

    // ---------------------------------------------------------------- 试算

    /**
     * 下单试算:不落库、不清空购物车。
     *
     * <p>门店打烊时**不报错**,而是返回 {@code shopOpen=false} 让前端禁用提交按钮(R1 只约束下单)。
     * 地址没传时用默认地址;一条地址都没有 → 422 {@code ORDER_ADDRESS_INVALID}。
     */
    public PreviewResult preview(Long customerId, Long addressId) {
        List<CartService.CheckoutLine> lines = cartService.linesForCheckout(customerId);
        if (lines.isEmpty()) {
            throw new BusinessException(OrderErrorCode.ORDER_CART_EMPTY);
        }
        DeliveryAddressView address = resolveAddress(customerId, addressId);
        requireAllOnSale(lines);

        List<PreviewLine> items = lines.stream()
                .map(line -> new PreviewLine(
                        line.itemType(), line.dishId(), line.setmealId(),
                        line.goods().name(), line.goods().imageUrl(), line.goods().priceCents(),
                        line.quantity(), unitPrice(line) * line.quantity(), line.flavorChoice()))
                .toList();
        return new PreviewResult(amounts(lines), items, address, shopStatusService.isOpen());
    }

    // ---------------------------------------------------------------- 下单

    /**
     * 下单(R3:校验 + 快照 + 清空购物车 + 生成待付款订单,同一事务)。
     *
     * @param expectedTotalAmountCents 可选;与重算出的商品合计不一致 → 422 {@code ORDER_PRICE_CHANGED}
     */
    @Transactional
    public SubmitResult submit(Long customerId, Long addressId, String remark,
                               Integer tablewareCount, Long expectedTotalAmountCents) {
        List<CartService.CheckoutLine> lines = cartService.linesForCheckout(customerId);
        if (lines.isEmpty()) {
            throw new BusinessException(OrderErrorCode.ORDER_CART_EMPTY);
        }

        String dupKey = DUP_KEY_PREFIX + customerId;
        String fingerprint = fingerprint(addressId, remark, tablewareCount, lines);
        if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(dupKey, fingerprint, DUPLICATE_SUBMIT_WINDOW))) {
            if (fingerprint.equals(redis.opsForValue().get(dupKey))) {
                throw new BusinessException(OrderErrorCode.ORDER_DUPLICATE_SUBMIT);
            }
            // 内容不同的另一单:放行,并把指纹换成自己的
            redis.opsForValue().set(dupKey, fingerprint, DUPLICATE_SUBMIT_WINDOW);
        }

        try {
            return persist(customerId, addressId, remark, tablewareCount, expectedTotalAmountCents, lines);
        } catch (RuntimeException ex) {
            // 校验失败不能把顾客锁在 10 秒窗口里:改了地址/购物车后应当能立刻重试
            redis.delete(dupKey);
            throw ex;
        }
    }

    private SubmitResult persist(Long customerId, Long addressId, String remark, Integer tablewareCount,
                                 Long expectedTotalAmountCents, List<CartService.CheckoutLine> lines) {
        if (!shopStatusService.isOpen()) {
            throw new BusinessException(OrderErrorCode.ORDER_SHOP_CLOSED);
        }
        DeliveryAddressView address = resolveAddress(customerId, addressId);
        requireAllOnSale(lines);

        Amounts amounts = amounts(lines);
        if (expectedTotalAmountCents != null && expectedTotalAmountCents != amounts.totalAmountCents()) {
            throw new BusinessException(OrderErrorCode.ORDER_PRICE_CHANGED,
                    "商品价格已变化,请刷新后重新提交",
                    List.of(new ErrorResponse.Detail("expectedTotalAmountCents",
                            "服务端重算商品合计为 " + amounts.totalAmountCents() + " 分")));
        }

        Order order = new Order();
        order.setOrderNo(orderNoGenerator.next());
        order.setCustomerId(customerId);
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setTotalAmountCents(amounts.totalAmountCents());
        order.setPackAmountCents(amounts.packAmountCents());
        order.setDeliveryAmountCents(amounts.deliveryAmountCents());
        order.setDiscountAmountCents(amounts.discountAmountCents());
        order.setPayAmountCents(amounts.payAmountCents());
        order.setPayStatus(PayStatus.UNPAID);
        order.setConsignee(address.consignee());
        order.setPhone(address.phone());
        order.setProvince(address.province());
        order.setCity(address.city());
        order.setDistrict(address.district());
        order.setDetail(address.detail());
        order.setSourceAddressId(address.id());
        order.setRemark(remark);
        order.setTablewareCount(tablewareCount == null ? 1 : tablewareCount);
        order.setPlacedAt(Times.nowLocal());
        orderMapper.insert(order);

        for (CartService.CheckoutLine line : lines) {
            orderItemMapper.insert(toOrderItem(order.getId(), line));
        }
        // 跨上下文写操作放在同一个事务里(后端架构 §4.4):下单成功即清空购物车
        cartService.clear(customerId);

        return new SubmitResult(order.getId(), order.getOrderNo(), order.getStatus(),
                order.getPayAmountCents(), true);
    }

    // ---------------------------------------------------------------- 查询

    public PageResponse<Order> page(Long customerId, OrderStatus status, LocalDate placedAtFrom, LocalDate placedAtTo,
                                    PageQuery pageQuery, SortSpec sortSpec) {
        LambdaQueryWrapper<Order> wrapper = Wrappers.lambdaQuery();
        if (customerId != null) {
            wrapper.eq(Order::getCustomerId, customerId);
        }
        if (status != null) {
            wrapper.eq(Order::getStatus, status);
        }
        if (placedAtFrom != null) {
            wrapper.ge(Order::getPlacedAt, placedAtFrom.atStartOfDay());
        }
        if (placedAtTo != null) {
            // 含首含尾:结束日是当天 23:59:59,所以用"次日 0 点前"
            wrapper.lt(Order::getPlacedAt, placedAtTo.plusDays(1).atStartOfDay());
        }
        applySort(wrapper, sortSpec);

        Page<Order> page = orderMapper.selectPage(new Page<>(pageQuery.page(), pageQuery.pageSize()), wrapper);
        fillItemCounts(page.getRecords());
        return PageResponse.from(page);
    }

    public Order requireOwned(Long customerId, Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null || !customerId.equals(order.getCustomerId())) {
            throw new BusinessException(OrderErrorCode.ORDER_NOT_FOUND);
        }
        return order;
    }

    public List<OrderItem> itemsOf(Long orderId) {
        return orderItemMapper.selectList(Wrappers.<OrderItem>lambdaQuery()
                .eq(OrderItem::getOrderId, orderId)
                .orderByAsc(OrderItem::getId));
    }

    // ---------------------------------------------------------------- 取消、再来一单、催单

    /**
     * 顾客取消订单。
     *
     * <p>两种拒绝分得清楚:
     * <ul>
     *   <li>状态机不允许的迁移(已取消/已完成/派送中)→ 422 {@code ORDER_INVALID_TRANSITION};</li>
     *   <li>迁移本身合法但顾客无权(商家才可取消的待接单/已接单)→ 422 {@code ORDER_CANNOT_CANCEL}。
     *       已支付订单必须走退款流程(R6),不能在这里"假取消"。</li>
     * </ul>
     */
    @Transactional
    public void cancelByCustomer(Long customerId, Long orderId, String reason) {
        Order order = requireOwned(customerId, orderId);
        OrderStateMachine.requireTransition(order.getStatus(), OrderStatus.CANCELLED);
        if (!order.getStatus().isUnpaid()) {
            throw new BusinessException(OrderErrorCode.ORDER_CANNOT_CANCEL);
        }
        Order update = new Order();
        update.setId(orderId);
        update.setStatus(OrderStatus.CANCELLED);
        update.setCancelSide(CancelSide.CUSTOMER);
        update.setCancelReason(reason);
        update.setCancelledAt(Times.nowLocal());
        orderMapper.updateById(update);
    }

    /** 再来一单:把明细重新加回购物车,不自动下单;下架/已删除的商品跳过并列出来。 */
    @Transactional
    public ReorderResult reorder(Long customerId, Long orderId) {
        Order order = requireOwned(customerId, orderId);
        List<SkippedItem> skipped = new ArrayList<>();
        int added = 0;
        for (OrderItem item : itemsOf(order.getId())) {
            if (item.getDishId() == null && item.getSetmealId() == null) {
                skipped.add(new SkippedItem(item.getNameSnapshot(), "商品已不存在"));
                continue;
            }
            String reason = cartService.addOrSkip(customerId, item.getItemType().name(),
                    item.getDishId(), item.getSetmealId(), item.getQuantity(),
                    item.getFlavorSnapshot().stream()
                            .map(flavor -> new CartService.FlavorChoiceRef(flavor.name(), flavor.option()))
                            .toList());
            if (reason == null) {
                added++;
            } else {
                skipped.add(new SkippedItem(item.getNameSnapshot(), reason));
            }
        }
        return new ReorderResult(added, skipped);
    }

    /** 催单:仅进行中的订单可催,同订单 5 分钟一次。不产生持久状态。 */
    public void remind(Long customerId, Long orderId, String message) {
        Order order = requireOwned(customerId, orderId);
        if (!switch (order.getStatus()) {
            case PENDING_ACCEPTANCE, ACCEPTED, DELIVERING -> true;
            default -> false;
        }) {
            throw new BusinessException(OrderErrorCode.ORDER_URGE_NOT_ALLOWED);
        }
        String key = URGE_KEY_PREFIX + orderId;
        if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, "1", URGE_WINDOW))) {
            throw new BusinessException(OrderErrorCode.ORDER_URGE_TOO_FREQUENT);
        }
        orderNotifier.orderReminder(orderId, order.getOrderNo(), message);
    }

    // ---------------------------------------------------------------- 跨上下文:支付

    /**
     * 支付上下文需要的订单视图。跨上下文不能引用 {@code order.domain.Order}(架构规则 L4),
     * 所以这里是 service 包里的一个稳定投影。
     */
    public record OrderPaymentView(Long id, String orderNo, String status, String payStatus,
                                   long payAmountCents, String payMethod) {
    }

    public Optional<OrderPaymentView> findPaymentViewByOrderNo(String orderNo) {
        Order order = orderMapper.selectOne(Wrappers.<Order>lambdaQuery().eq(Order::getOrderNo, orderNo));
        return Optional.ofNullable(order).map(OrderService::toPaymentView);
    }

    public OrderPaymentView requirePaymentView(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(OrderErrorCode.ORDER_NOT_FOUND);
        }
        return toPaymentView(order);
    }

    /** 顾客侧用:既要支付视图,又要 R9 归属校验(他人订单一律 404)。 */
    public OrderPaymentView requireOwnedPaymentView(Long customerId, Long orderId) {
        return toPaymentView(requireOwned(customerId, orderId));
    }

    /** 批量取订单号(退款列表展示用),避免每行一次查询。 */
    public Map<Long, String> orderNosByIds(Collection<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> result = new LinkedHashMap<>();
        for (Order order : orderMapper.selectBatchIds(orderIds)) {
            result.put(order.getId(), order.getOrderNo());
        }
        return result;
    }

    /**
     * 支付成功:待付款 → 待接单(走状态机),写 {@code paidAt} / {@code payStatus} / {@code payMethod}。
     *
     * <p>幂等:已支付的订单直接返回。支付回调可能重复送达(R7),这里必须是"重复通知只生效一次"的第一道闸。
     */
    @Transactional
    public void markPaid(Long orderId, String payMethod) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(OrderErrorCode.ORDER_NOT_FOUND);
        }
        if (order.getPayStatus() != null && order.getPayStatus().isPaid()) {
            return;
        }
        OrderStateMachine.requireTransition(order.getStatus(), OrderStatus.PENDING_ACCEPTANCE);

        Order update = new Order();
        update.setId(orderId);
        update.setStatus(OrderStatus.PENDING_ACCEPTANCE);
        update.setPayStatus(PayStatus.PAID);
        update.setPayMethod(parsePayMethod(payMethod));
        update.setPaidAt(Times.nowLocal());
        orderMapper.updateById(update);
    }

    /** 退款成功:订单 {@code payStatus} 置 REFUNDED。订单状态本身不动——取消/拒单流程各自负责状态迁移。 */
    @Transactional
    public void markRefunded(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(OrderErrorCode.ORDER_NOT_FOUND);
        }
        if (order.getPayStatus() == PayStatus.REFUNDED) {
            return;
        }
        Order update = new Order();
        update.setId(orderId);
        update.setPayStatus(PayStatus.REFUNDED);
        orderMapper.updateById(update);
    }

    private static OrderPaymentView toPaymentView(Order order) {
        return new OrderPaymentView(
                order.getId(),
                order.getOrderNo(),
                order.getStatus() == null ? null : order.getStatus().name(),
                order.getPayStatus() == null ? null : order.getPayStatus().name(),
                order.getPayAmountCents() == null ? 0L : order.getPayAmountCents(),
                order.getPayMethod() == null ? null : order.getPayMethod().name());
    }

    private static PayMethod parsePayMethod(String payMethod) {
        if (payMethod == null) {
            return null;
        }
        try {
            return PayMethod.valueOf(payMethod);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(CommonErrorCode.COMMON_VALIDATION_FAILED,
                    "支付方式不合法",
                    List.of(new ErrorResponse.Detail("payMethod", "只能是 WECHAT 或 MOCK")));
        }
    }

    // ---------------------------------------------------------------- 内部

    private DeliveryAddressView resolveAddress(Long customerId, Long addressId) {
        return (addressId == null
                ? addressService.findPreferredDeliveryAddress(customerId)
                : addressService.findDeliveryAddress(customerId, addressId))
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_ADDRESS_INVALID));
    }

    private static void requireAllOnSale(List<CartService.CheckoutLine> lines) {
        for (CartService.CheckoutLine line : lines) {
            if (line.goods() == null || !line.goods().available()) {
                throw new BusinessException(OrderErrorCode.ORDER_ITEM_NOT_ON_SALE,
                        "有商品已下架,请刷新购物车",
                        List.of(new ErrorResponse.Detail("items",
                                (line.goods() == null ? "商品已下架" : line.goods().unavailableReason()))));
            }
        }
    }

    private static long unitPrice(CartService.CheckoutLine line) {
        return line.goods() == null || line.goods().priceCents() == null ? 0L : line.goods().priceCents();
    }

    private static Amounts amounts(List<CartService.CheckoutLine> lines) {
        long total = lines.stream().mapToLong(line -> unitPrice(line) * line.quantity()).sum();
        long pay = total + PACK_AMOUNT_CENTS + DELIVERY_AMOUNT_CENTS;
        return new Amounts(total, PACK_AMOUNT_CENTS, DELIVERY_AMOUNT_CENTS, 0L, pay);
    }

    private OrderItem toOrderItem(Long orderId, CartService.CheckoutLine line) {
        OrderItem item = new OrderItem();
        item.setOrderId(orderId);
        item.setItemType(ItemType.valueOf(line.itemType()));
        item.setDishId(line.dishId());
        item.setSetmealId(line.setmealId());
        item.setNameSnapshot(line.goods().name());
        item.setImageSnapshot(line.goods().imageUrl());
        item.setUnitPriceCents(unitPrice(line));
        item.setQuantity(line.quantity());
        item.setAmountCents(unitPrice(line) * line.quantity());

        if (ItemType.valueOf(line.itemType()) == ItemType.DISH) {
            item.setFlavorSnapshot(line.flavorChoice().stream()
                    .map(flavor -> new FlavorChoice(flavor.name(), flavor.option()))
                    .toList());
        } else {
            // 套餐要把"所含菜品"也快照下来:套餐组成随时可改,订单必须能还原下单那一刻
            item.setComboSnapshot(setmealService.itemsOf(line.setmealId()).stream()
                    .map(combo -> new ComboSnapshotItem(combo.dishId(), combo.dishName(), combo.copies()))
                    .toList());
        }
        return item;
    }

    private void fillItemCounts(List<Order> orders) {
        List<Long> ids = orders.stream().map(Order::getId).toList();
        if (ids.isEmpty()) {
            return;
        }
        Map<Long, Integer> counts = new LinkedHashMap<>();
        for (OrderItemCount row : orderItemMapper.countByOrderIds(ids)) {
            counts.put(row.getOrderId(), row.getItemCount());
        }
        orders.forEach(order -> order.setItemCount(counts.getOrDefault(order.getId(), 0)));
    }

    /**
     * 重复提交指纹。
     *
     * <p>按**商品内容**算(商品、数量、单价、口味)而不是购物车行 id:顾客清空购物车后重新加入
     * 同样商品再提交,行 id 会变,用行 id 会让防护失效;而且"同一份内容 10 秒内提交两次"本来就该被拦。
     * 口味与商品行都排序,保证与购物车顺序无关。
     */
    private static String fingerprint(Long addressId, String remark, Integer tablewareCount,
                                      Collection<CartService.CheckoutLine> lines) {
        String content = lines.stream()
                .map(line -> {
                    String flavor = line.flavorChoice().stream()
                            .map(choice -> choice.name() + "=" + choice.option())
                            .sorted()
                            .collect(Collectors.joining(","));
                    String goods = line.dishId() != null ? "d" + line.dishId() : "s" + line.setmealId();
                    return line.itemType() + ":" + goods + ":" + line.quantity() + ":" + unitPrice(line) + ":" + flavor;
                })
                .sorted()
                .collect(Collectors.joining("|"));
        String raw = addressId + "#" + (StringUtils.hasText(remark) ? remark : "") + "#" + tablewareCount + "#" + content;
        return Hashes.sha256Hex(raw);
    }

    private void applySort(LambdaQueryWrapper<Order> wrapper, SortSpec sortSpec) {
        switch (sortSpec.field()) {
            case "placedAt" -> wrapper.orderBy(true, sortSpec.ascending(), Order::getPlacedAt);
            case "payAmountCents" -> wrapper.orderBy(true, sortSpec.ascending(), Order::getPayAmountCents);
            default -> throw new BusinessException(CommonErrorCode.COMMON_SORT_FIELD_NOT_ALLOWED);
        }
        // 稳定分页:下单时间可能同秒,用 id 兜住
        wrapper.orderByDesc(Order::getId);
    }

    /** 供调试/测试读取"预计送达时间"的策略:v2 不做配送调度(领域文档 §7),恒为空。 */
    public static LocalDateTime estimatedDeliveryAt() {
        return null;
    }
}
