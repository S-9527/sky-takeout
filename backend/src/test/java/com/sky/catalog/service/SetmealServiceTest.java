package com.sky.catalog.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

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
import com.sky.testsupport.TableInfoTestSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SetmealServiceTest {

    private final SetmealMapper setmealMapper = mock(SetmealMapper.class);
    private final SetmealItemMapper setmealItemMapper = mock(SetmealItemMapper.class);
    private final DishMapper dishMapper = mock(DishMapper.class);
    private final CategoryService categoryService = mock(CategoryService.class);

    private final SetmealService setmealService =
            new SetmealService(setmealMapper, setmealItemMapper, dishMapper, categoryService);

    @BeforeAll
    static void registerTableInfo() {
        // lambdaQuery/lambdaUpdate 需要 lambda 缓存,单测里没有 Spring 需要显式注册
        TableInfoTestSupport.register(Setmeal.class, SetmealItem.class, Dish.class);
    }

    // ---------------------------------------------------------------- 辅助

    private static Category category(long id, CategoryType type, EnableStatus status) {
        Category category = new Category();
        category.setId(id);
        category.setName(type == CategoryType.SETMEAL ? "单人套餐" : "川湘菜");
        category.setType(type);
        category.setStatus(status);
        return category;
    }

    private static Dish dish(long id, long priceCents, EnableStatus status) {
        Dish dish = new Dish();
        dish.setId(id);
        dish.setCategoryId(1L);
        dish.setName("菜品" + id);
        dish.setPriceCents(priceCents);
        dish.setStatus(status);
        return dish;
    }

    private static Setmeal setmeal(long id, long categoryId, long priceCents, EnableStatus status) {
        Setmeal setmeal = new Setmeal();
        setmeal.setId(id);
        setmeal.setCategoryId(categoryId);
        setmeal.setName("套餐" + id);
        setmeal.setPriceCents(priceCents);
        setmeal.setStatus(status);
        return setmeal;
    }

    private static SetmealItem item(long id, long setmealId, long dishId, int copies) {
        SetmealItem item = new SetmealItem();
        item.setId(id);
        item.setSetmealId(setmealId);
        item.setDishId(dishId);
        item.setCopies(copies);
        return item;
    }

    private static SetmealService.ItemInput input(long dishId, int copies) {
        return new SetmealService.ItemInput(dishId, copies);
    }

    private void categoryIs(long id, CategoryType type, EnableStatus status) {
        when(categoryService.requireById(id)).thenReturn(category(id, type, status));
    }

    private void dishesAre(Dish... dishes) {
        when(dishMapper.selectBatchIds(any())).thenReturn(List.of(dishes));
    }

    // ---------------------------------------------------------------- 分页与查询

    @Test
    void pageAppliesFiltersAndReturnsMetadata() {
        Page<Setmeal> page = new Page<>(1, 20);
        page.setRecords(List.of(setmeal(201L, 7L, 4500L, EnableStatus.ENABLED)));
        page.setTotal(5);
        when(setmealMapper.selectPage(any(), any())).thenReturn(page);

        PageResponse<Setmeal> result = setmealService.page("川味", 7L, 1, PageQuery.of(1, 20),
                new SortSpec("createdAt", true));

        assertThat(result.records()).hasSize(1);
        assertThat(result.total()).isEqualTo(5);
    }

    @Test
    void pageRejectsUnknownSortFieldAndIllegalStatus() {
        assertThatThrownBy(() -> setmealService.page(null, null, null, PageQuery.of(1, 20),
                new SortSpec("sortOrder", false)))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(CommonErrorCode.COMMON_SORT_FIELD_NOT_ALLOWED));

        assertThatThrownBy(() -> setmealService.page(null, null, 9, PageQuery.of(1, 20),
                new SortSpec("createdAt", true)))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(CommonErrorCode.COMMON_VALIDATION_FAILED));
    }

    @Test
    void pageSupportsEveryWhitelistedSortField() {
        Page<Setmeal> page = new Page<>(1, 20);
        page.setRecords(List.of());
        when(setmealMapper.selectPage(any(), any())).thenReturn(page);

        for (String field : SetmealService.SORT_WHITELIST) {
            setmealService.page(null, null, null, PageQuery.of(1, 20), new SortSpec(field, true));
            setmealService.page(null, null, null, PageQuery.of(1, 20), new SortSpec(field, false));
        }

        assertThat(SetmealService.SORT_WHITELIST).containsExactlyInAnyOrder("priceCents", "createdAt", "name");
    }

    @Test
    void requireByIdReturnsSetmealOr404() {
        when(setmealMapper.selectById(201L)).thenReturn(setmeal(201L, 7L, 4500L, EnableStatus.ENABLED));
        assertThat(setmealService.requireById(201L).getPriceCents()).isEqualTo(4500L);

        when(setmealMapper.selectById(404L)).thenReturn(null);
        assertThatThrownBy(() -> setmealService.requireById(404L))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(CatalogErrorCode.SETMEAL_NOT_FOUND));
    }

    @Test
    void itemsOfEnrichesWithDishNameAndCurrentPrice() {
        when(setmealItemMapper.selectList(any())).thenReturn(List.of(item(1L, 201L, 101L, 2)));
        dishesAre(dish(101L, 3800L, EnableStatus.ENABLED));

        List<SetmealService.ItemView> items = setmealService.itemsOf(201L);

        assertThat(items).hasSize(1);
        assertThat(items.get(0).dishName()).isEqualTo("菜品101");
        assertThat(items.get(0).dishPriceCents()).isEqualTo(3800L);
        assertThat(items.get(0).copies()).isEqualTo(2);
    }

    @Test
    void itemsOfReturnsEmptyListWithoutQueryingDishes() {
        when(setmealItemMapper.selectList(any())).thenReturn(List.of());

        assertThat(setmealService.itemsOf(201L)).isEmpty();
        verify(dishMapper, never()).selectBatchIds(any());
    }

    // ---------------------------------------------------------------- 新增

    @Test
    void createDefaultsToOffSaleAndWritesItems() {
        categoryIs(7L, CategoryType.SETMEAL, EnableStatus.ENABLED);
        when(setmealMapper.exists(any())).thenReturn(false);
        when(setmealMapper.insert(any(Setmeal.class))).thenAnswer(invocation -> {
            ((Setmeal) invocation.getArgument(0)).setId(300L);
            return 1;
        });
        when(setmealMapper.selectById(300L)).thenReturn(setmeal(300L, 7L, 4100L, EnableStatus.DISABLED));
        dishesAre(dish(101L, 3800L, EnableStatus.ENABLED), dish(112L, 300L, EnableStatus.ENABLED));

        setmealService.create(7L, "新套餐", 4100L, null, null, null, List.of(input(101L, 1), input(112L, 1)));

        ArgumentCaptor<Setmeal> inserted = ArgumentCaptor.forClass(Setmeal.class);
        verify(setmealMapper).insert(inserted.capture());
        assertThat(inserted.getValue().getStatus()).isEqualTo(EnableStatus.DISABLED);

        ArgumentCaptor<SetmealItem> items = ArgumentCaptor.forClass(SetmealItem.class);
        verify(setmealItemMapper, times(2)).insert(items.capture());
        assertThat(items.getAllValues().get(0).getSetmealId()).isEqualTo(300L);
        assertThat(items.getAllValues().get(0).getDishId()).isEqualTo(101L);
    }

    @Test
    void createCanStartSellingWhenAllDishesAreOnSale() {
        categoryIs(7L, CategoryType.SETMEAL, EnableStatus.ENABLED);
        when(setmealMapper.exists(any())).thenReturn(false);
        when(setmealMapper.selectById(any())).thenReturn(setmeal(300L, 7L, 100L, EnableStatus.ENABLED));
        dishesAre(dish(101L, 3800L, EnableStatus.ENABLED));

        setmealService.create(7L, "新套餐", 100L, null, null, 1, List.of(input(101L, 1)));

        verify(setmealMapper).insert(any(Setmeal.class));
    }

    @Test
    void createRejectsStartingWithOffSaleDish() {
        categoryIs(7L, CategoryType.SETMEAL, EnableStatus.ENABLED);
        when(setmealMapper.exists(any())).thenReturn(false);
        dishesAre(dish(101L, 3800L, EnableStatus.DISABLED));

        assertThatThrownBy(() -> setmealService.create(7L, "新套餐", 100L, null, null, 1, List.of(input(101L, 1))))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.errorCode()).isEqualTo(CatalogErrorCode.SETMEAL_DISH_NOT_ON_SALE);
                    assertThat(ex.details()).isNotEmpty();
                });
        verify(setmealMapper, never()).insert(any(Setmeal.class));
    }

    @Test
    void createRejectsEmptyOrMissingItems() {
        categoryIs(7L, CategoryType.SETMEAL, EnableStatus.ENABLED);
        when(setmealMapper.exists(any())).thenReturn(false);

        for (List<SetmealService.ItemInput> items : List.of(List.<SetmealService.ItemInput>of())) {
            assertThatThrownBy(() -> setmealService.create(7L, "新套餐", 100L, null, null, 1, items))
                    .isInstanceOfSatisfying(BusinessException.class,
                            ex -> assertThat(ex.errorCode()).isEqualTo(CatalogErrorCode.SETMEAL_ITEMS_EMPTY));
        }
        assertThatThrownBy(() -> setmealService.create(7L, "新套餐", 100L, null, null, 1, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(CatalogErrorCode.SETMEAL_ITEMS_EMPTY));
    }

    @Test
    void createRejectsDuplicatedDishInItems() {
        categoryIs(7L, CategoryType.SETMEAL, EnableStatus.ENABLED);
        when(setmealMapper.exists(any())).thenReturn(false);

        assertThatThrownBy(() -> setmealService.create(7L, "新套餐", 100L, null, null, 1,
                List.of(input(101L, 1), input(101L, 2))))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(CatalogErrorCode.SETMEAL_ITEMS_DUPLICATED));
    }

    @Test
    void createRejectsUnknownDish() {
        categoryIs(7L, CategoryType.SETMEAL, EnableStatus.ENABLED);
        when(setmealMapper.exists(any())).thenReturn(false);
        dishesAre(dish(101L, 3800L, EnableStatus.ENABLED));

        assertThatThrownBy(() -> setmealService.create(7L, "新套餐", 100L, null, null, 1,
                List.of(input(101L, 1), input(999L, 1))))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(CatalogErrorCode.DISH_NOT_FOUND));
    }

    @Test
    void createRejectsPriceAboveItemsTotal() {
        categoryIs(7L, CategoryType.SETMEAL, EnableStatus.ENABLED);
        when(setmealMapper.exists(any())).thenReturn(false);
        dishesAre(dish(101L, 3800L, EnableStatus.ENABLED));

        // 3800 x 1 = 3800 < 3801
        assertThatThrownBy(() -> setmealService.create(7L, "新套餐", 3801L, null, null, 1, List.of(input(101L, 1))))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.errorCode()).isEqualTo(CatalogErrorCode.SETMEAL_PRICE_EXCEEDS_ITEMS);
                    assertThat(ex.details()).isNotEmpty();
                });
    }

    /** 边界:定价恰好等于合计是允许的(规则是"≤")。 */
    @Test
    void createAllowsPriceEqualToItemsTotal() {
        categoryIs(7L, CategoryType.SETMEAL, EnableStatus.ENABLED);
        when(setmealMapper.exists(any())).thenReturn(false);
        when(setmealMapper.selectById(any())).thenReturn(setmeal(300L, 7L, 7600L, EnableStatus.ENABLED));
        dishesAre(dish(101L, 3800L, EnableStatus.ENABLED));

        setmealService.create(7L, "新套餐", 7600L, null, null, 1, List.of(input(101L, 2)));

        verify(setmealMapper).insert(any(Setmeal.class));
    }

    @Test
    void createRejectsDishCategory() {
        categoryIs(1L, CategoryType.DISH, EnableStatus.ENABLED);

        assertThatThrownBy(() -> setmealService.create(1L, "新套餐", 100L, null, null, 1, List.of(input(101L, 1))))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(CatalogErrorCode.SETMEAL_CATEGORY_TYPE_MISMATCH));
    }

    @Test
    void createRejectsDisabledCategory() {
        categoryIs(7L, CategoryType.SETMEAL, EnableStatus.DISABLED);

        assertThatThrownBy(() -> setmealService.create(7L, "新套餐", 100L, null, null, 1, List.of(input(101L, 1))))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(CatalogErrorCode.CATEGORY_DISABLED));
    }

    @Test
    void createRejectsDuplicateName() {
        categoryIs(7L, CategoryType.SETMEAL, EnableStatus.ENABLED);
        when(setmealMapper.exists(any())).thenReturn(true);

        assertThatThrownBy(() -> setmealService.create(7L, "单人川味套餐", 100L, null, null, 1, List.of(input(101L, 1))))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(CatalogErrorCode.SETMEAL_NAME_TAKEN));
    }

    @Test
    void createRejectsInvalidCopies() {
        categoryIs(7L, CategoryType.SETMEAL, EnableStatus.ENABLED);
        when(setmealMapper.exists(any())).thenReturn(false);

        assertThatThrownBy(() -> setmealService.create(7L, "新套餐", 100L, null, null, 1,
                List.of(new SetmealService.ItemInput(101L, 0))))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(CommonErrorCode.COMMON_VALIDATION_FAILED));
    }

    // ---------------------------------------------------------------- 编辑

    @Test
    void updateWithoutItemsKeepsThemAndValidatesAgainstCurrentOnes() {
        when(setmealMapper.selectById(201L)).thenReturn(setmeal(201L, 7L, 4500L, EnableStatus.ENABLED));
        categoryIs(7L, CategoryType.SETMEAL, EnableStatus.ENABLED);
        when(setmealMapper.exists(any())).thenReturn(false);
        when(setmealItemMapper.selectList(any())).thenReturn(List.of(item(1L, 201L, 101L, 1)));
        dishesAre(dish(101L, 3800L, EnableStatus.ENABLED));

        // 现有所含菜品合计 3800,把价格改到 4000 必须被拦下
        assertThatThrownBy(() -> setmealService.update(201L, 7L, "单人川味套餐", 4000L, null, null, 1, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(CatalogErrorCode.SETMEAL_PRICE_EXCEEDS_ITEMS));
        verify(setmealItemMapper, never()).delete(any());
    }

    @Test
    void updateWithItemsReplacesThem() {
        when(setmealMapper.selectById(201L)).thenReturn(setmeal(201L, 7L, 4500L, EnableStatus.ENABLED));
        categoryIs(7L, CategoryType.SETMEAL, EnableStatus.ENABLED);
        when(setmealMapper.exists(any())).thenReturn(false);
        dishesAre(dish(101L, 3800L, EnableStatus.ENABLED), dish(112L, 300L, EnableStatus.ENABLED));

        setmealService.update(201L, 7L, "单人川味套餐", 4100L, null, null, 1,
                List.of(input(101L, 1), input(112L, 1)));

        verify(setmealItemMapper).delete(any());
        verify(setmealItemMapper, times(2)).insert(any(SetmealItem.class));
    }

    /** 起售校验必须用**最终生效**的组成:先换成停售菜品再起售也要被拦住。 */
    @Test
    void updateRejectsStartingWhenEffectiveItemsContainOffSaleDish() {
        when(setmealMapper.selectById(201L)).thenReturn(setmeal(201L, 7L, 4500L, EnableStatus.DISABLED));
        categoryIs(7L, CategoryType.SETMEAL, EnableStatus.ENABLED);
        when(setmealMapper.exists(any())).thenReturn(false);
        dishesAre(dish(101L, 100L, EnableStatus.ENABLED), dish(102L, 100L, EnableStatus.DISABLED));

        assertThatThrownBy(() -> setmealService.update(201L, 7L, "单人川味套餐", 200L, null, null, 1,
                List.of(input(101L, 1), input(102L, 1))))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(CatalogErrorCode.SETMEAL_DISH_NOT_ON_SALE));
        verify(setmealMapper, never()).updateById(any(Setmeal.class));
    }

    @Test
    void updateRejectsDuplicateNameAndWrongCategory() {
        when(setmealMapper.selectById(201L)).thenReturn(setmeal(201L, 7L, 4500L, EnableStatus.DISABLED));
        categoryIs(7L, CategoryType.SETMEAL, EnableStatus.ENABLED);
        when(setmealMapper.exists(any())).thenReturn(true);
        when(setmealItemMapper.selectList(any())).thenReturn(List.of(item(1L, 201L, 101L, 1)));
        dishesAre(dish(101L, 3800L, EnableStatus.ENABLED));

        assertThatThrownBy(() -> setmealService.update(201L, 7L, "重名套餐", 100L, null, null, 0, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(CatalogErrorCode.SETMEAL_NAME_TAKEN));

        categoryIs(1L, CategoryType.DISH, EnableStatus.ENABLED);
        assertThatThrownBy(() -> setmealService.update(201L, 1L, "单人川味套餐", 100L, null, null, 0, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(CatalogErrorCode.SETMEAL_CATEGORY_TYPE_MISMATCH));
    }

    @Test
    void updateRejectsUnknownSetmeal() {
        when(setmealMapper.selectById(404L)).thenReturn(null);

        assertThatThrownBy(() -> setmealService.update(404L, 7L, "任意", 100L, null, null, 0, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(CatalogErrorCode.SETMEAL_NOT_FOUND));
    }

    // ---------------------------------------------------------------- 删除与批量状态

    @Test
    void deleteRemovesAllRequestedSetmeals() {
        when(setmealMapper.selectBatchIds(any())).thenReturn(List.of(
                setmeal(201L, 7L, 4500L, EnableStatus.ENABLED), setmeal(202L, 7L, 3900L, EnableStatus.ENABLED)));

        setmealService.delete(List.of(201L, 202L));

        verify(setmealMapper).deleteByIds(List.of(201L, 202L));
    }

    @Test
    void deleteRejectsWhenAnyIdIsMissing() {
        when(setmealMapper.selectBatchIds(any())).thenReturn(List.of(setmeal(201L, 7L, 4500L, EnableStatus.ENABLED)));

        assertThatThrownBy(() -> setmealService.delete(List.of(201L, 999L)))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(CatalogErrorCode.SETMEAL_NOT_FOUND));
        verify(setmealMapper, never()).deleteByIds(any());
    }

    @Test
    void changeStatusStartingChecksEveryDish() {
        when(setmealMapper.selectBatchIds(any())).thenReturn(List.of(setmeal(201L, 7L, 4500L, EnableStatus.DISABLED)));
        when(setmealItemMapper.selectList(any())).thenReturn(List.of(item(1L, 201L, 101L, 1)));
        dishesAre(dish(101L, 3800L, EnableStatus.DISABLED));

        assertThatThrownBy(() -> setmealService.changeStatus(List.of(201L), EnableStatus.ENABLED))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(CatalogErrorCode.SETMEAL_DISH_NOT_ON_SALE));
        verify(setmealMapper, never()).update(any(Setmeal.class), any());
    }

    @Test
    void changeStatusStartingWorksWhenAllDishesOnSale() {
        when(setmealMapper.selectBatchIds(any())).thenReturn(List.of(setmeal(201L, 7L, 4500L, EnableStatus.DISABLED)));
        when(setmealItemMapper.selectList(any())).thenReturn(List.of(item(1L, 201L, 101L, 1)));
        dishesAre(dish(101L, 3800L, EnableStatus.ENABLED));

        setmealService.changeStatus(List.of(201L), EnableStatus.ENABLED);

        verify(setmealMapper).update(any(Setmeal.class), any());
    }

    @Test
    void changeStatusStoppingSkipsDishChecks() {
        when(setmealMapper.selectBatchIds(any())).thenReturn(List.of(setmeal(201L, 7L, 4500L, EnableStatus.ENABLED)));

        setmealService.changeStatus(List.of(201L), EnableStatus.DISABLED);

        verify(setmealItemMapper, never()).selectList(any());
        verify(setmealMapper).update(any(Setmeal.class), any());
    }

    @Test
    void changeStatusRejectsUnknownIds() {
        when(setmealMapper.selectBatchIds(any())).thenReturn(List.of());

        assertThatThrownBy(() -> setmealService.changeStatus(List.of(999L), EnableStatus.DISABLED))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(CatalogErrorCode.SETMEAL_NOT_FOUND));
    }

    // ---------------------------------------------------------------- 顾客端可见性

    @Test
    void customerPageReturnsEmptyWhenNoSetmealCategoryIsVisible() {
        when(categoryService.list(CategoryType.SETMEAL, false)).thenReturn(List.of());

        PageResponse<Setmeal> result = setmealService.pageCustomerVisible(null, PageQuery.of(1, 20));

        assertThat(result.records()).isEmpty();
        verify(setmealMapper, never()).selectPage(any(), any());
    }

    @Test
    void customerPageFiltersByVisibleCategoriesAndOptionalCategoryId() {
        when(categoryService.list(CategoryType.SETMEAL, false))
                .thenReturn(List.of(category(7L, CategoryType.SETMEAL, EnableStatus.ENABLED)));
        Page<Setmeal> page = new Page<>(1, 20);
        page.setRecords(List.of(setmeal(201L, 7L, 4500L, EnableStatus.ENABLED)));
        page.setTotal(1);
        when(setmealMapper.selectPage(any(), any())).thenReturn(page);

        assertThat(setmealService.pageCustomerVisible(null, PageQuery.of(1, 20)).records()).hasSize(1);
        assertThat(setmealService.pageCustomerVisible(7L, PageQuery.of(1, 20)).records()).hasSize(1);
    }

    /** 可选筛选条件给了不存在的分类 → 空页,不是 404(契约里这个接口只有 400)。 */
    @Test
    void customerPageWithUnknownCategoryFilterReturnsEmptyPage() {
        when(categoryService.list(CategoryType.SETMEAL, false))
                .thenReturn(List.of(category(7L, CategoryType.SETMEAL, EnableStatus.ENABLED)));
        Page<Setmeal> page = new Page<>(1, 20);
        page.setRecords(List.of());
        page.setTotal(0);
        when(setmealMapper.selectPage(any(), any())).thenReturn(page);

        PageResponse<Setmeal> result = setmealService.pageCustomerVisible(999L, PageQuery.of(1, 20));

        assertThat(result.records()).isEmpty();
        assertThat(result.total()).isZero();
    }

    @Test
    void customerDetailRejectsUnknownSetmealWith404() {
        when(setmealMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> setmealService.requireCustomerVisible(999L))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(CatalogErrorCode.SETMEAL_NOT_FOUND));
    }

    @Test
    void customerDetailRejectsOffSaleSetmeal() {
        when(setmealMapper.selectById(201L)).thenReturn(setmeal(201L, 7L, 4500L, EnableStatus.DISABLED));

        assertThatThrownBy(() -> setmealService.requireCustomerVisible(201L))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(CatalogErrorCode.SETMEAL_OFF_SALE));
    }

    @Test
    void customerDetailRejectsSetmealWhoseCategoryBecameInvisible() {
        when(setmealMapper.selectById(201L)).thenReturn(setmeal(201L, 7L, 4500L, EnableStatus.ENABLED));
        when(categoryService.requireById(7L)).thenReturn(category(7L, CategoryType.SETMEAL, EnableStatus.DISABLED));

        assertThatThrownBy(() -> setmealService.requireCustomerVisible(201L))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.errorCode()).isEqualTo(CatalogErrorCode.SETMEAL_OFF_SALE);
                    assertThat(ex.details()).isNotEmpty();
                });
    }

    @Test
    void customerDetailReturnsOnSaleSetmealInVisibleCategory() {
        when(setmealMapper.selectById(201L)).thenReturn(setmeal(201L, 7L, 4500L, EnableStatus.ENABLED));
        when(categoryService.requireById(7L)).thenReturn(category(7L, CategoryType.SETMEAL, EnableStatus.ENABLED));

        assertThat(setmealService.requireCustomerVisible(201L).getId()).isEqualTo(201L);
    }

    // ---------------------------------------------------------------- 跨上下文:可售视图

    @Test
    void purchasableViewReportsAvailability() {
        when(setmealMapper.selectById(201L)).thenReturn(setmeal(201L, 7L, 4500L, EnableStatus.ENABLED));
        when(categoryService.byIds(any()))
                .thenReturn(java.util.Map.of(7L, category(7L, CategoryType.SETMEAL, EnableStatus.ENABLED)));

        var view = setmealService.purchasable(201L);

        assertThat(view.available()).isTrue();
        assertThat(view.name()).isEqualTo("套餐201");
        assertThat(view.categoryName()).isEqualTo("单人套餐");
    }

    @Test
    void purchasableViewExplainsWhyItIsNotAvailable() {
        when(setmealMapper.selectById(201L)).thenReturn(setmeal(201L, 7L, 4500L, EnableStatus.DISABLED));
        when(categoryService.byIds(any()))
                .thenReturn(java.util.Map.of(7L, category(7L, CategoryType.SETMEAL, EnableStatus.ENABLED)));
        assertThat(setmealService.purchasable(201L).unavailableReason()).isEqualTo("套餐已停售");

        when(setmealMapper.selectById(202L)).thenReturn(setmeal(202L, 8L, 3900L, EnableStatus.ENABLED));
        when(categoryService.byIds(any()))
                .thenReturn(java.util.Map.of(8L, category(8L, CategoryType.SETMEAL, EnableStatus.DISABLED)));
        assertThat(setmealService.purchasable(202L).unavailableReason()).isEqualTo("套餐所属分类已停用");
    }

    @Test
    void purchasableViewRejectsUnknownSetmeal() {
        when(setmealMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> setmealService.purchasable(999L))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(CatalogErrorCode.SETMEAL_NOT_FOUND));
    }

    @Test
    void purchasableByIdsReturnsBatchMapAndShortCircuitsOnEmpty() {
        assertThat(setmealService.purchasableByIds(List.of())).isEmpty();
        verify(setmealMapper, never()).selectBatchIds(any());

        when(setmealMapper.selectBatchIds(any())).thenReturn(List.of(setmeal(201L, 7L, 4500L, EnableStatus.ENABLED)));
        when(categoryService.byIds(any()))
                .thenReturn(java.util.Map.of(7L, category(7L, CategoryType.SETMEAL, EnableStatus.ENABLED)));

        assertThat(setmealService.purchasableByIds(List.of(201L))).containsOnlyKeys(201L);
    }
}
