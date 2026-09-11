package com.sky.catalog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.sky.catalog.domain.CatalogErrorCode;
import com.sky.catalog.domain.Category;
import com.sky.catalog.domain.CategoryType;
import com.sky.catalog.domain.Dish;
import com.sky.catalog.domain.Setmeal;
import com.sky.catalog.domain.SetmealItem;
import com.sky.catalog.mapper.DishMapper;
import com.sky.catalog.mapper.SetmealItemMapper;
import com.sky.catalog.mapper.SetmealMapper;
import com.sky.common.domain.EnableStatus;
import com.sky.common.domain.PageQuery;
import com.sky.common.domain.PageResponse;
import com.sky.common.domain.SortSpec;
import com.sky.common.error.BusinessException;
import com.sky.common.error.CommonErrorCode;
import com.sky.common.error.ErrorResponse;

/**
 * 套餐的读写,含组成明细整体替换、定价校验与起售前置校验。
 *
 * <p>三条规则在这里落地(领域文档 §3.6):
 * 套餐价 ≤ 所含菜品单价×份数之和;起售时所含菜品必须全部起售;同一菜品在组成里只能出现一次。
 */
@Service
public class SetmealService {

    /** 与 openapi 的 pageSetmeals 描述一致(注意没有 sortOrder:setmeal 表没有该列)。 */
    public static final Set<String> SORT_WHITELIST = Set.of("priceCents", "createdAt", "name");

    private final SetmealMapper setmealMapper;
    private final SetmealItemMapper setmealItemMapper;
    private final DishMapper dishMapper;
    private final CategoryService categoryService;

    public SetmealService(SetmealMapper setmealMapper, SetmealItemMapper setmealItemMapper,
                          DishMapper dishMapper, CategoryService categoryService) {
        this.setmealMapper = setmealMapper;
        this.setmealItemMapper = setmealItemMapper;
        this.dishMapper = dishMapper;
        this.categoryService = categoryService;
    }

    /** 组成明细入参(服务层类型,避免 service 依赖 api 的 DTO)。 */
    public record ItemInput(Long dishId, Integer copies) {
    }

    /** 组成明细出参,含联表取到的菜品名与当前单价——前端编辑页与列表展示都要用。 */
    public record ItemView(Long id, Long dishId, String dishName, Long dishPriceCents, Integer copies) {
    }

    public PageResponse<Setmeal> page(String name, Long categoryId, Integer status,
                                      PageQuery pageQuery, SortSpec sortSpec) {
        LambdaQueryWrapper<Setmeal> wrapper = Wrappers.lambdaQuery();
        if (StringUtils.hasText(name)) {
            wrapper.like(Setmeal::getName, name);
        }
        if (categoryId != null) {
            wrapper.eq(Setmeal::getCategoryId, categoryId);
        }
        if (status != null) {
            wrapper.eq(Setmeal::getStatus, EnableStatus.of(status));
        }
        applySort(wrapper, sortSpec);

        Page<Setmeal> page = setmealMapper.selectPage(new Page<>(pageQuery.page(), pageQuery.pageSize()), wrapper);
        return PageResponse.from(page);
    }

    public Setmeal requireById(Long id) {
        Setmeal setmeal = setmealMapper.selectById(id);
        if (setmeal == null) {
            throw new BusinessException(CatalogErrorCode.SETMEAL_NOT_FOUND);
        }
        return setmeal;
    }

    /**
     * 顾客端套餐列表:只返回起售套餐,且其分类必须启用。
     *
     * <p>{@code categoryId} 可选。分类不存在或不可见时返回**空页**而不是 404——
     * 这个参数是筛选条件,不是路径资源(契约的 03-api 索引也只列了 COMMON_VALIDATION_FAILED)。
     */
    public PageResponse<Setmeal> pageCustomerVisible(Long categoryId, PageQuery pageQuery) {
        List<Long> visibleCategoryIds = categoryService.list(CategoryType.SETMEAL, false).stream()
                .map(Category::getId)
                .toList();
        if (visibleCategoryIds.isEmpty()) {
            return PageResponse.empty(pageQuery.page(), pageQuery.pageSize());
        }
        LambdaQueryWrapper<Setmeal> wrapper = Wrappers.<Setmeal>lambdaQuery()
                .in(Setmeal::getCategoryId, visibleCategoryIds)
                .eq(Setmeal::getStatus, EnableStatus.ENABLED);
        if (categoryId != null) {
            wrapper.eq(Setmeal::getCategoryId, categoryId);
        }
        wrapper.orderByDesc(Setmeal::getCreatedAt).orderByAsc(Setmeal::getId);

        Page<Setmeal> page = setmealMapper.selectPage(new Page<>(pageQuery.page(), pageQuery.pageSize()), wrapper);
        return PageResponse.from(page);
    }

    /** 顾客端套餐详情:不存在 → 404;停售、或所在分类不可见 → 422 {@code SETMEAL_OFF_SALE}。 */
    public Setmeal requireCustomerVisible(Long id) {
        Setmeal setmeal = requireById(id);
        if (setmeal.getStatus() == null || !setmeal.getStatus().isEnabled()) {
            throw new BusinessException(CatalogErrorCode.SETMEAL_OFF_SALE);
        }
        Category category = categoryService.requireById(setmeal.getCategoryId());
        if (category.getType() != CategoryType.SETMEAL
                || category.getStatus() == null
                || !category.getStatus().isEnabled()) {
            throw new BusinessException(CatalogErrorCode.SETMEAL_OFF_SALE, "套餐已下架,请重新选择",
                    List.of(new ErrorResponse.Detail("categoryId", "所属分类不可见")));
        }
        return setmeal;
    }

    /**
     * 套餐组成明细,带上菜品名称与当前单价(联表取实时值,不做快照——快照只发生在下单那一刻)。
     *
     * <p>菜品不可能被真删:被套餐引用时外键 RESTRICT 会挡住删除,所以这里直接复用
     * {@link #loadDishes};真要缺失也只会是 404,不会是 500。
     */
    public List<ItemView> itemsOf(Long setmealId) {
        List<SetmealItem> items = setmealItemMapper.selectList(Wrappers.<SetmealItem>lambdaQuery()
                .eq(SetmealItem::getSetmealId, setmealId)
                .orderByAsc(SetmealItem::getId));
        if (items.isEmpty()) {
            return List.of();
        }
        Map<Long, Dish> dishes = loadDishes(items.stream().map(SetmealItem::getDishId).toList());
        return items.stream()
                .map(item -> {
                    Dish dish = dishes.get(item.getDishId());
                    return new ItemView(item.getId(), item.getDishId(),
                            dish == null ? null : dish.getName(),
                            dish == null ? null : dish.getPriceCents(),
                            item.getCopies());
                })
                .toList();
    }

    @Transactional
    public Setmeal create(Long categoryId, String name, Long priceCents, String imageUrl, String description,
                          Integer status, List<ItemInput> items) {
        requireSetmealCategory(categoryId, true);
        requireNameAvailable(categoryId, name, null);
        List<ItemInput> normalized = requireUsableItems(items);
        Map<Long, Dish> dishes = loadDishes(dishIdsOf(normalized));
        requirePriceWithinItems(priceCents, normalized, dishes);

        // 不传就停售:与菜品一致,上架必须是显式操作
        EnableStatus target = status == null ? EnableStatus.DISABLED : EnableStatus.of(status);
        if (target.isEnabled()) {
            requireAllDishesOnSale(dishes.values());
        }

        Setmeal setmeal = new Setmeal();
        setmeal.setCategoryId(categoryId);
        setmeal.setName(name);
        setmeal.setPriceCents(priceCents);
        setmeal.setImageUrl(imageUrl);
        setmeal.setDescription(description);
        setmeal.setStatus(target);
        setmealMapper.insert(setmeal);

        insertItems(setmeal.getId(), normalized);
        return requireById(setmeal.getId());
    }

    /**
     * 编辑套餐。
     *
     * @param items {@code null} 表示不改组成明细;传了则整体替换。无论哪种情况,定价与起售校验
     *              都按**最终生效的**组成来算——否则可以先换成贵的菜再把价格调高。
     */
    @Transactional
    public Setmeal update(Long id, Long categoryId, String name, Long priceCents, String imageUrl,
                          String description, Integer status, List<ItemInput> items) {
        Setmeal existing = requireById(id);
        boolean categoryChanged = !categoryId.equals(existing.getCategoryId());
        requireSetmealCategory(categoryId, categoryChanged);
        requireNameAvailable(categoryId, name, id);

        List<ItemInput> effectiveItems = items == null ? currentItems(id) : requireUsableItems(items);
        Map<Long, Dish> dishes = loadDishes(dishIdsOf(effectiveItems));
        requirePriceWithinItems(priceCents, effectiveItems, dishes);

        EnableStatus target = EnableStatus.of(status);
        if (target.isEnabled()) {
            requireAllDishesOnSale(dishes.values());
        }

        Setmeal update = new Setmeal();
        update.setId(id);
        update.setCategoryId(categoryId);
        update.setName(name);
        update.setPriceCents(priceCents);
        update.setImageUrl(imageUrl);
        update.setDescription(description);
        update.setStatus(target);
        setmealMapper.updateById(update);

        if (items != null) {
            replaceItems(id, effectiveItems);
        }
        return requireById(id);
    }

    /** 批量真删除;组成明细由外键级联删除。历史订单不受影响(订单存的是快照)。 */
    @Transactional
    public void delete(List<Long> ids) {
        requireAllExist(ids);
        setmealMapper.deleteByIds(ids);
    }

    /** 批量起售 / 停售。起售前必须确认所含菜品全部在售,否则 422。 */
    @Transactional
    public void changeStatus(List<Long> ids, EnableStatus status) {
        requireAllExist(ids);
        if (status.isEnabled()) {
            for (Long id : ids) {
                requireAllDishesOnSale(loadDishes(dishIdsOf(currentItems(id))).values());
            }
        }
        Setmeal update = new Setmeal();
        update.setStatus(status);
        setmealMapper.update(update, Wrappers.<Setmeal>lambdaUpdate().in(Setmeal::getId, ids));
    }

    // ---------------------------------------------------------------- 跨上下文:可售视图

    /** 单个套餐的可售视图。不存在 → 404 {@code SETMEAL_NOT_FOUND}。 */
    public PurchasableItemView purchasable(Long setmealId) {
        Setmeal setmeal = requireById(setmealId);
        Category category = categoryService.byIds(List.of(setmeal.getCategoryId())).get(setmeal.getCategoryId());
        return toPurchasableView(setmeal, category);
    }

    /** 批量版本,供购物车/订单一次取多件(避免 N+1)。 */
    public Map<Long, PurchasableItemView> purchasableByIds(Collection<Long> setmealIds) {
        if (setmealIds == null || setmealIds.isEmpty()) {
            return Map.of();
        }
        List<Setmeal> setmeals = setmealMapper.selectBatchIds(setmealIds);
        if (setmeals.isEmpty()) {
            return Map.of();
        }
        Map<Long, Category> categories = categoryService.byIds(
                setmeals.stream().map(Setmeal::getCategoryId).collect(Collectors.toSet()));
        Map<Long, PurchasableItemView> result = new LinkedHashMap<>();
        for (Setmeal setmeal : setmeals) {
            result.put(setmeal.getId(), toPurchasableView(setmeal, categories.get(setmeal.getCategoryId())));
        }
        return result;
    }

    private static PurchasableItemView toPurchasableView(Setmeal setmeal, Category category) {
        boolean onSale = setmeal.getStatus() != null && setmeal.getStatus().isEnabled();
        boolean categoryVisible = category != null
                && category.getType() == CategoryType.SETMEAL
                && category.getStatus() != null
                && category.getStatus().isEnabled();
        boolean available = onSale && categoryVisible;
        String reason = available ? null : (onSale ? "套餐所属分类已停用" : "套餐已停售");
        return new PurchasableItemView(
                setmeal.getId(), setmeal.getName(), setmeal.getImageUrl(), setmeal.getPriceCents(),
                category == null ? null : category.getId(),
                category == null ? null : category.getName(),
                available, reason);
    }

    // ---------------------------------------------------------------- 内部校验

    private void requireAllExist(List<Long> ids) {
        if (setmealMapper.selectBatchIds(ids).size() != ids.size()) {
            throw new BusinessException(CatalogErrorCode.SETMEAL_NOT_FOUND);
        }
    }

    private Category requireSetmealCategory(Long categoryId, boolean mustBeEnabled) {
        Category category = categoryService.requireById(categoryId);
        if (category.getType() != CategoryType.SETMEAL) {
            throw new BusinessException(CatalogErrorCode.SETMEAL_CATEGORY_TYPE_MISMATCH);
        }
        if (mustBeEnabled && (category.getStatus() == null || !category.getStatus().isEnabled())) {
            throw new BusinessException(CatalogErrorCode.CATEGORY_DISABLED);
        }
        return category;
    }

    private void requireNameAvailable(Long categoryId, String name, Long excludeId) {
        LambdaQueryWrapper<Setmeal> wrapper = Wrappers.<Setmeal>lambdaQuery()
                .eq(Setmeal::getCategoryId, categoryId)
                .eq(Setmeal::getName, name);
        if (excludeId != null) {
            wrapper.ne(Setmeal::getId, excludeId);
        }
        if (setmealMapper.exists(wrapper)) {
            throw new BusinessException(CatalogErrorCode.SETMEAL_NAME_TAKEN);
        }
    }

    /** 明细不能为空,菜品与份数必须合法;去重后按原顺序返回。 */
    private List<ItemInput> requireUsableItems(List<ItemInput> items) {
        if (items == null || items.isEmpty()) {
            throw new BusinessException(CatalogErrorCode.SETMEAL_ITEMS_EMPTY);
        }
        List<ItemInput> normalized = new ArrayList<>(items.size());
        Set<Long> seen = new HashSet<>();
        for (int index = 0; index < items.size(); index++) {
            ItemInput item = items.get(index);
            if (item == null || item.dishId() == null) {
                throw new BusinessException(CommonErrorCode.COMMON_VALIDATION_FAILED,
                        "组成明细必须给出 dishId",
                        List.of(new ErrorResponse.Detail("items[" + index + "].dishId", "不能为空")));
            }
            if (item.copies() == null || item.copies() < 1) {
                throw new BusinessException(CommonErrorCode.COMMON_VALIDATION_FAILED,
                        "份数至少为 1",
                        List.of(new ErrorResponse.Detail("items[" + index + "].copies", "至少为 1")));
            }
            if (!seen.add(item.dishId())) {
                throw new BusinessException(CatalogErrorCode.SETMEAL_ITEMS_DUPLICATED,
                        "同一菜品不能重复添加",
                        List.of(new ErrorResponse.Detail("items[" + index + "].dishId", "该菜品已在本套餐中")));
            }
            normalized.add(item);
        }
        return normalized;
    }

    private Map<Long, Dish> loadDishes(Collection<Long> dishIds) {
        if (dishIds.isEmpty()) {
            return Map.of();
        }
        List<Dish> dishes = dishMapper.selectBatchIds(dishIds);
        if (dishes.size() != new HashSet<>(dishIds).size()) {
            throw new BusinessException(CatalogErrorCode.DISH_NOT_FOUND);
        }
        return dishes.stream().collect(Collectors.toMap(Dish::getId, Function.identity(),
                (first, second) -> first, LinkedHashMap::new));
    }

    /** 套餐定价不允许高于所含菜品的原价合计(领域文档 §3.6:不允许凭空加价)。 */
    private void requirePriceWithinItems(Long priceCents, List<ItemInput> items, Map<Long, Dish> dishes) {
        long itemsTotal = 0L;
        for (ItemInput item : items) {
            Dish dish = dishes.get(item.dishId());
            if (dish != null && dish.getPriceCents() != null) {
                itemsTotal += dish.getPriceCents() * item.copies();
            }
        }
        if (priceCents != null && priceCents > itemsTotal) {
            throw new BusinessException(CatalogErrorCode.SETMEAL_PRICE_EXCEEDS_ITEMS,
                    "套餐定价不能高于所含菜品合计",
                    List.of(new ErrorResponse.Detail("priceCents", "当前所含菜品合计为 " + itemsTotal + " 分")));
        }
    }

    private void requireAllDishesOnSale(Collection<Dish> dishes) {
        for (Dish dish : dishes) {
            if (dish.getStatus() == null || !dish.getStatus().isEnabled()) {
                throw new BusinessException(CatalogErrorCode.SETMEAL_DISH_NOT_ON_SALE,
                        "所含菜品中有停售商品,无法起售",
                        List.of(new ErrorResponse.Detail("items", "菜品「" + dish.getName() + "」已停售")));
            }
        }
    }

    private List<ItemInput> currentItems(Long setmealId) {        return setmealItemMapper.selectList(Wrappers.<SetmealItem>lambdaQuery()
                        .eq(SetmealItem::getSetmealId, setmealId)
                        .orderByAsc(SetmealItem::getId)).stream()
                .map(item -> new ItemInput(item.getDishId(), item.getCopies()))
                .toList();
    }

    private void replaceItems(Long setmealId, List<ItemInput> items) {
        setmealItemMapper.delete(Wrappers.<SetmealItem>lambdaQuery().eq(SetmealItem::getSetmealId, setmealId));
        insertItems(setmealId, items);
    }

    private void insertItems(Long setmealId, List<ItemInput> items) {
        for (ItemInput item : items) {
            SetmealItem entity = new SetmealItem();
            entity.setSetmealId(setmealId);
            entity.setDishId(item.dishId());
            entity.setCopies(item.copies());
            setmealItemMapper.insert(entity);
        }
    }

    private static List<Long> dishIdsOf(List<ItemInput> items) {
        return items.stream().map(ItemInput::dishId).distinct().toList();
    }

    private void applySort(LambdaQueryWrapper<Setmeal> wrapper, SortSpec sortSpec) {
        switch (sortSpec.field()) {
            case "priceCents" -> wrapper.orderBy(true, sortSpec.ascending(), Setmeal::getPriceCents);
            case "createdAt" -> wrapper.orderBy(true, sortSpec.ascending(), Setmeal::getCreatedAt);
            case "name" -> wrapper.orderBy(true, sortSpec.ascending(), Setmeal::getName);
            default -> throw new BusinessException(CommonErrorCode.COMMON_SORT_FIELD_NOT_ALLOWED);
        }
        wrapper.orderByAsc(Setmeal::getId);
    }
}
