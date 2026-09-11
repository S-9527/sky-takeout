package com.sky.catalog.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;

import com.sky.catalog.domain.CatalogErrorCode;
import com.sky.catalog.domain.Category;
import com.sky.catalog.domain.CategoryType;
import com.sky.catalog.domain.Dish;
import com.sky.catalog.domain.DishFlavor;
import com.sky.catalog.mapper.DishFlavorMapper;
import com.sky.catalog.mapper.DishMapper;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DishServiceTest {

    private final DishMapper dishMapper = mock(DishMapper.class);
    private final DishFlavorMapper dishFlavorMapper = mock(DishFlavorMapper.class);
    private final CategoryService categoryService = mock(CategoryService.class);

    private final DishService dishService = new DishService(dishMapper, dishFlavorMapper, categoryService);

    @BeforeAll
    static void registerTableInfo() {
        // lambdaUpdate().in(Dish::getId, ...) 会立刻查 lambda 缓存,单测里没有 Spring 需要显式注册
        TableInfoTestSupport.register(Dish.class, DishFlavor.class);
    }

    private static Category category(long id, CategoryType type, EnableStatus status) {
        Category category = new Category();
        category.setId(id);
        category.setName(type == CategoryType.DISH ? "川湘菜" : "单人套餐");
        category.setType(type);
        category.setStatus(status);
        return category;
    }

    private static Dish dish(long id, long categoryId, EnableStatus status) {
        Dish dish = new Dish();
        dish.setId(id);
        dish.setCategoryId(categoryId);
        dish.setName("宫保鸡丁");
        dish.setPriceCents(3800L);
        dish.setStatus(status);
        dish.setSortOrder(1);
        return dish;
    }

    private static DishService.FlavorInput flavor(String name, String... options) {
        return new DishService.FlavorInput(name, List.of(options), null);
    }

    private void categoryIs(long id, CategoryType type, EnableStatus status) {
        when(categoryService.requireById(id)).thenReturn(category(id, type, status));
    }

    // ---------------------------------------------------------------- 分页与查询

    @Test
    void pageAppliesFiltersAndReturnsMetadata() {
        Page<Dish> page = new Page<>(1, 20);
        page.setRecords(List.of(dish(101L, 1L, EnableStatus.ENABLED)));
        page.setTotal(21);
        when(dishMapper.selectPage(any(), any())).thenReturn(page);

        PageResponse<Dish> result = dishService.page("宫保", 1L, 1, PageQuery.of(1, 20),
                new SortSpec("sortOrder", false));

        assertThat(result.records()).hasSize(1);
        assertThat(result.total()).isEqualTo(21);
    }

    @Test
    void pageRejectsSortFieldOutsideWhitelistAndIllegalStatus() {
        assertThatThrownBy(() -> dishService.page(null, null, null, PageQuery.of(1, 20), new SortSpec("id", false)))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(CommonErrorCode.COMMON_SORT_FIELD_NOT_ALLOWED));

        assertThatThrownBy(() -> dishService.page(null, null, 7, PageQuery.of(1, 20), new SortSpec("sortOrder", false)))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(CommonErrorCode.COMMON_VALIDATION_FAILED));
    }

    @Test
    void pageSupportsEveryWhitelistedSortField() {
        Page<Dish> page = new Page<>(1, 20);
        page.setRecords(List.of());
        when(dishMapper.selectPage(any(), any())).thenReturn(page);

        for (String field : DishService.SORT_WHITELIST) {
            dishService.page(null, null, null, PageQuery.of(1, 20), new SortSpec(field, true));
            dishService.page(null, null, null, PageQuery.of(1, 20), new SortSpec(field, false));
        }

        assertThat(DishService.SORT_WHITELIST)
                .containsExactlyInAnyOrder("sortOrder", "priceCents", "createdAt", "name");
    }

    @Test
    void requireByIdReturnsDishOr404() {
        when(dishMapper.selectById(101L)).thenReturn(dish(101L, 1L, EnableStatus.ENABLED));
        assertThat(dishService.requireById(101L).getName()).isEqualTo("宫保鸡丁");

        when(dishMapper.selectById(404L)).thenReturn(null);
        assertThatThrownBy(() -> dishService.requireById(404L))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(CatalogErrorCode.DISH_NOT_FOUND));
    }

    // ---------------------------------------------------------------- 新增

    @Test
    void createDefaultsToOffSaleAndSortOrderZero() {
        categoryIs(1L, CategoryType.DISH, EnableStatus.ENABLED);
        when(dishMapper.exists(any())).thenReturn(false);
        when(dishMapper.selectById(any())).thenReturn(dish(200L, 1L, EnableStatus.DISABLED));

        dishService.create(1L, "新菜", 1000L, null, null, null, null, null);

        ArgumentCaptor<Dish> inserted = ArgumentCaptor.forClass(Dish.class);
        verify(dishMapper).insert(inserted.capture());
        assertThat(inserted.getValue().getStatus()).isEqualTo(EnableStatus.DISABLED);
        assertThat(inserted.getValue().getSortOrder()).isZero();
        verify(dishMapper, never()).disableSetmealsContaining(any());
    }

    @Test
    void createWritesFlavorsInOrder() {
        categoryIs(1L, CategoryType.DISH, EnableStatus.ENABLED);
        when(dishMapper.exists(any())).thenReturn(false);
        when(dishMapper.selectById(any())).thenReturn(dish(200L, 1L, EnableStatus.ENABLED));

        dishService.create(1L, "新菜", 1000L, null, null, 1, 5,
                List.of(flavor("辣度", "不辣", "微辣"), flavor("忌口", "不要葱")));

        ArgumentCaptor<DishFlavor> flavors = ArgumentCaptor.forClass(DishFlavor.class);
        verify(dishFlavorMapper, org.mockito.Mockito.times(2)).insert(flavors.capture());
        assertThat(flavors.getAllValues().get(0).getName()).isEqualTo("辣度");
        assertThat(flavors.getAllValues().get(0).getOptions()).containsExactly("不辣", "微辣");
        assertThat(flavors.getAllValues().get(0).getSortOrder()).isZero();
        assertThat(flavors.getAllValues().get(1).getSortOrder()).isEqualTo(1);
    }

    @Test
    void createRejectsUnknownCategory() {
        when(categoryService.requireById(999L))
                .thenThrow(new BusinessException(CatalogErrorCode.CATEGORY_NOT_FOUND));

        assertThatThrownBy(() -> dishService.create(999L, "新菜", 1000L, null, null, 1, 0, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(CatalogErrorCode.CATEGORY_NOT_FOUND));
        verify(dishMapper, never()).insert(any(Dish.class));
    }

    @Test
    void createRejectsSetmealCategory() {
        categoryIs(7L, CategoryType.SETMEAL, EnableStatus.ENABLED);

        assertThatThrownBy(() -> dishService.create(7L, "新菜", 1000L, null, null, 1, 0, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(CatalogErrorCode.DISH_CATEGORY_TYPE_MISMATCH));
    }

    @Test
    void createRejectsDisabledCategory() {
        categoryIs(2L, CategoryType.DISH, EnableStatus.DISABLED);

        assertThatThrownBy(() -> dishService.create(2L, "新菜", 1000L, null, null, 1, 0, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(CatalogErrorCode.CATEGORY_DISABLED));
    }

    @Test
    void createRejectsDuplicateNameWithinCategory() {
        categoryIs(1L, CategoryType.DISH, EnableStatus.ENABLED);
        when(dishMapper.exists(any())).thenReturn(true);

        assertThatThrownBy(() -> dishService.create(1L, "宫保鸡丁", 1000L, null, null, 1, 0, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(CatalogErrorCode.DISH_NAME_TAKEN));
        verify(dishMapper, never()).insert(any(Dish.class));
    }

    @Test
    void createRejectsDuplicateFlavorNames() {
        categoryIs(1L, CategoryType.DISH, EnableStatus.ENABLED);
        when(dishMapper.exists(any())).thenReturn(false);
        when(dishMapper.selectById(any())).thenReturn(dish(200L, 1L, EnableStatus.ENABLED));

        assertThatThrownBy(() -> dishService.create(1L, "新菜", 1000L, null, null, 1, 0,
                List.of(flavor("辣度", "不辣"), flavor("辣度", "微辣"))))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.errorCode()).isEqualTo(CommonErrorCode.COMMON_VALIDATION_FAILED);
                    assertThat(ex.details()).isNotEmpty();
                });
    }

    @Test
    void createRejectsFlavorWithoutOptionsOrName() {
        categoryIs(1L, CategoryType.DISH, EnableStatus.ENABLED);
        when(dishMapper.exists(any())).thenReturn(false);
        when(dishMapper.selectById(any())).thenReturn(dish(200L, 1L, EnableStatus.ENABLED));

        assertThatThrownBy(() -> dishService.create(1L, "新菜", 1000L, null, null, 1, 0,
                List.of(new DishService.FlavorInput("辣度", List.of(), null))))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(CommonErrorCode.COMMON_VALIDATION_FAILED));

        assertThatThrownBy(() -> dishService.create(1L, "新菜", 1000L, null, null, 1, 0,
                List.of(new DishService.FlavorInput(" ", List.of("不辣"), null))))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(CommonErrorCode.COMMON_VALIDATION_FAILED));
    }

    // ---------------------------------------------------------------- 编辑

    @Test
    void updateKeepsFlavorsWhenNullIsPassed() {
        Dish existing = dish(101L, 1L, EnableStatus.ENABLED);
        when(dishMapper.selectById(101L)).thenReturn(existing);
        categoryIs(1L, CategoryType.DISH, EnableStatus.ENABLED);
        when(dishMapper.exists(any())).thenReturn(false);

        dishService.update(101L, 1L, "宫保鸡丁(大份)", 4200L, null, null, 1, 2, null);

        verify(dishFlavorMapper, never()).delete(any());
        verify(dishFlavorMapper, never()).insert(any(DishFlavor.class));
        verify(dishMapper, never()).disableSetmealsContaining(any());
    }

    @Test
    void updateWithEmptyFlavorListClearsConfiguration() {
        when(dishMapper.selectById(101L)).thenReturn(dish(101L, 1L, EnableStatus.ENABLED));
        categoryIs(1L, CategoryType.DISH, EnableStatus.ENABLED);
        when(dishMapper.exists(any())).thenReturn(false);

        dishService.update(101L, 1L, "宫保鸡丁", 3800L, null, null, 1, 1, List.of());

        verify(dishFlavorMapper).delete(any());
        verify(dishFlavorMapper, never()).insert(any(DishFlavor.class));
    }

    /** 分类已被禁用时,不改分类的普通编辑不应该被挡住。 */
    @Test
    void updateInSameCategoryWorksEvenIfCategoryWasDisabledLater() {
        when(dishMapper.selectById(101L)).thenReturn(dish(101L, 2L, EnableStatus.ENABLED));
        categoryIs(2L, CategoryType.DISH, EnableStatus.DISABLED);
        when(dishMapper.exists(any())).thenReturn(false);

        dishService.update(101L, 2L, "宫保鸡丁", 3800L, null, null, 1, 1, null);

        verify(dishMapper).updateById(any(Dish.class));
    }

    @Test
    void updateRejectsMovingIntoDisabledCategory() {
        when(dishMapper.selectById(101L)).thenReturn(dish(101L, 1L, EnableStatus.ENABLED));
        categoryIs(2L, CategoryType.DISH, EnableStatus.DISABLED);

        assertThatThrownBy(() -> dishService.update(101L, 2L, "宫保鸡丁", 3800L, null, null, 1, 1, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(CatalogErrorCode.CATEGORY_DISABLED));
        verify(dishMapper, never()).updateById(any(Dish.class));
    }

    @Test
    void updateRejectsMovingIntoSetmealCategory() {
        when(dishMapper.selectById(101L)).thenReturn(dish(101L, 1L, EnableStatus.ENABLED));
        categoryIs(7L, CategoryType.SETMEAL, EnableStatus.ENABLED);

        assertThatThrownBy(() -> dishService.update(101L, 7L, "宫保鸡丁", 3800L, null, null, 1, 1, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(CatalogErrorCode.DISH_CATEGORY_TYPE_MISMATCH));
    }

    @Test
    void updateRejectsNameTakenByAnotherDish() {
        when(dishMapper.selectById(101L)).thenReturn(dish(101L, 1L, EnableStatus.ENABLED));
        categoryIs(1L, CategoryType.DISH, EnableStatus.ENABLED);
        when(dishMapper.exists(any())).thenReturn(true);

        assertThatThrownBy(() -> dishService.update(101L, 1L, "水煮牛肉", 3800L, null, null, 1, 1, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(CatalogErrorCode.DISH_NAME_TAKEN));
    }

    /** 停售菜品必须连带停售包含它的套餐(领域文档 §3.6,同一事务)。 */
    @Test
    void updateStoppingDishAlsoStopsSetmealsContainingIt() {
        when(dishMapper.selectById(101L)).thenReturn(dish(101L, 1L, EnableStatus.ENABLED));
        categoryIs(1L, CategoryType.DISH, EnableStatus.ENABLED);
        when(dishMapper.exists(any())).thenReturn(false);

        dishService.update(101L, 1L, "宫保鸡丁", 3800L, null, null, 0, 1, null);

        verify(dishMapper).disableSetmealsContaining(List.of(101L));
    }

    @Test
    void updateRejectsUnknownDish() {
        when(dishMapper.selectById(404L)).thenReturn(null);

        assertThatThrownBy(() -> dishService.update(404L, 1L, "任意", 1L, null, null, 1, 1, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(CatalogErrorCode.DISH_NOT_FOUND));
    }

    // ---------------------------------------------------------------- 删除与批量状态

    @Test
    void deleteRemovesAllRequestedDishes() {
        when(dishMapper.selectBatchIds(any())).thenReturn(List.of(
                dish(101L, 1L, EnableStatus.ENABLED), dish(102L, 1L, EnableStatus.ENABLED)));

        dishService.delete(List.of(101L, 102L));

        verify(dishMapper).deleteByIds(List.of(101L, 102L));
    }

    @Test
    void deleteRejectsWhenAnyIdIsMissing() {
        when(dishMapper.selectBatchIds(any())).thenReturn(List.of(dish(101L, 1L, EnableStatus.ENABLED)));

        assertThatThrownBy(() -> dishService.delete(List.of(101L, 999L)))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(CatalogErrorCode.DISH_NOT_FOUND));
        verify(dishMapper, never()).deleteByIds(any());
    }

    @Test
    void deleteTranslatesSetmealReferenceInto422() {
        when(dishMapper.selectBatchIds(any())).thenReturn(List.of(dish(101L, 1L, EnableStatus.ENABLED)));
        when(dishMapper.deleteByIds(any())).thenThrow(new DataIntegrityViolationException("fk_setmeal_item_dish"));

        assertThatThrownBy(() -> dishService.delete(List.of(101L)))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(CatalogErrorCode.SETMEAL_CONTAINS_DISH));
    }

    @Test
    void changeStatusStoppingDishesCascadesToSetmeals() {
        when(dishMapper.selectBatchIds(any())).thenReturn(List.of(
                dish(101L, 1L, EnableStatus.ENABLED), dish(102L, 1L, EnableStatus.ENABLED)));

        dishService.changeStatus(List.of(101L, 102L), EnableStatus.DISABLED);

        verify(dishMapper).update(any(Dish.class), any());
        verify(dishMapper).disableSetmealsContaining(List.of(101L, 102L));
    }

    @Test
    void changeStatusStartingDishesDoesNotTouchSetmeals() {
        when(dishMapper.selectBatchIds(any())).thenReturn(List.of(dish(101L, 1L, EnableStatus.DISABLED)));

        dishService.changeStatus(List.of(101L), EnableStatus.ENABLED);

        verify(dishMapper, never()).disableSetmealsContaining(any());
    }

    @Test
    void changeStatusRejectsUnknownDishIds() {
        when(dishMapper.selectBatchIds(any())).thenReturn(List.of());

        assertThatThrownBy(() -> dishService.changeStatus(List.of(999L), EnableStatus.DISABLED))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(CatalogErrorCode.DISH_NOT_FOUND));
        verify(dishMapper, never()).update(any(Dish.class), any());
    }

    @Test
    void flavorsOfReturnsMapperResultInOrder() {
        DishFlavor flavor = new DishFlavor();
        flavor.setId(1L);
        flavor.setName("辣度");
        flavor.setOptions(List.of("不辣", "微辣"));
        when(dishFlavorMapper.selectList(any())).thenReturn(List.of(flavor));

        assertThat(dishService.flavorsOf(101L)).hasSize(1);
        assertThat(dishService.flavorsOf(101L).get(0).getOptions()).containsExactly("不辣", "微辣");
        verify(dishFlavorMapper, org.mockito.Mockito.times(2)).selectList(any());
    }

    @Test
    void requireDishCategoryAlsoGuardsNullStatus() {
        Category category = category(3L, CategoryType.DISH, null);
        when(categoryService.requireById(3L)).thenReturn(category);

        assertThatThrownBy(() -> dishService.create(3L, "新菜", 1000L, null, null, 1, 0, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(CatalogErrorCode.CATEGORY_DISABLED));
        verify(dishMapper, never()).insert(any(Dish.class));
        verify(dishMapper, never()).selectById(anyLong());
    }

    // ---------------------------------------------------------------- 顾客端可见性

    @Test
    void customerPageRejectsUnknownCategory() {
        when(categoryService.requireById(999L))
                .thenThrow(new BusinessException(CatalogErrorCode.CATEGORY_NOT_FOUND));

        assertThatThrownBy(() -> dishService.pageCustomerVisible(999L, PageQuery.of(1, 20)))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(CatalogErrorCode.CATEGORY_NOT_FOUND));
    }

    /** 分类不可见时返回空页而不是报错:"没有可点的"和"分类藏起来了"对顾客是同一种结果。 */
    @Test
    void customerPageReturnsEmptyPageWhenCategoryIsInvisible() {
        categoryIs(2L, CategoryType.DISH, EnableStatus.DISABLED);
        PageResponse<Dish> disabled = dishService.pageCustomerVisible(2L, PageQuery.of(2, 50));

        assertThat(disabled.records()).isEmpty();
        assertThat(disabled.page()).isEqualTo(2);
        assertThat(disabled.pageSize()).isEqualTo(50);
        verify(dishMapper, never()).selectPage(any(), any());

        categoryIs(7L, CategoryType.SETMEAL, EnableStatus.ENABLED);
        assertThat(dishService.pageCustomerVisible(7L, PageQuery.of(1, 20)).records()).isEmpty();
    }

    @Test
    void customerPageReturnsOnSaleDishes() {
        categoryIs(1L, CategoryType.DISH, EnableStatus.ENABLED);
        Page<Dish> page = new Page<>(1, 20);
        page.setRecords(List.of(dish(101L, 1L, EnableStatus.ENABLED)));
        page.setTotal(1);
        when(dishMapper.selectPage(any(), any())).thenReturn(page);

        PageResponse<Dish> result = dishService.pageCustomerVisible(1L, PageQuery.of(1, 20));

        assertThat(result.records()).hasSize(1);
        assertThat(result.total()).isEqualTo(1);
    }

    @Test
    void customerDetailRejectsUnknownDishWith404() {
        when(dishMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> dishService.requireCustomerVisible(999L))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(CatalogErrorCode.DISH_NOT_FOUND));
    }

    @Test
    void customerDetailRejectsOffSaleDish() {
        when(dishMapper.selectById(101L)).thenReturn(dish(101L, 1L, EnableStatus.DISABLED));

        assertThatThrownBy(() -> dishService.requireCustomerVisible(101L))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(CatalogErrorCode.DISH_OFF_SALE));
    }

    @Test
    void customerDetailRejectsDishWhoseCategoryBecameInvisible() {
        when(dishMapper.selectById(101L)).thenReturn(dish(101L, 2L, EnableStatus.ENABLED));
        categoryIs(2L, CategoryType.DISH, EnableStatus.DISABLED);

        assertThatThrownBy(() -> dishService.requireCustomerVisible(101L))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.errorCode()).isEqualTo(CatalogErrorCode.DISH_OFF_SALE);
                    assertThat(ex.details()).isNotEmpty();
                });
    }

    @Test
    void customerDetailReturnsOnSaleDishInVisibleCategory() {
        when(dishMapper.selectById(101L)).thenReturn(dish(101L, 1L, EnableStatus.ENABLED));
        categoryIs(1L, CategoryType.DISH, EnableStatus.ENABLED);

        assertThat(dishService.requireCustomerVisible(101L).getId()).isEqualTo(101L);
    }

    // ---------------------------------------------------------------- 跨上下文:可售视图

    @Test
    void purchasableViewReportsAvailabilityAndCategory() {
        when(dishMapper.selectById(101L)).thenReturn(dish(101L, 1L, EnableStatus.ENABLED));
        when(categoryService.byIds(any())).thenReturn(java.util.Map.of(1L, category(1L, CategoryType.DISH, EnableStatus.ENABLED)));

        var view = dishService.purchasable(101L);

        assertThat(view.available()).isTrue();
        assertThat(view.name()).isEqualTo("宫保鸡丁");
        assertThat(view.categoryName()).isEqualTo("川湘菜");
        assertThat(view.unavailableReason()).isNull();
    }

    @Test
    void purchasableViewExplainsWhyItIsNotAvailable() {
        when(dishMapper.selectById(101L)).thenReturn(dish(101L, 1L, EnableStatus.DISABLED));
        when(categoryService.byIds(any())).thenReturn(java.util.Map.of(1L, category(1L, CategoryType.DISH, EnableStatus.ENABLED)));
        assertThat(dishService.purchasable(101L).unavailableReason()).isEqualTo("商品已停售");

        when(dishMapper.selectById(102L)).thenReturn(dish(102L, 2L, EnableStatus.ENABLED));
        when(categoryService.byIds(any())).thenReturn(java.util.Map.of(2L, category(2L, CategoryType.DISH, EnableStatus.DISABLED)));
        assertThat(dishService.purchasable(102L).unavailableReason()).isEqualTo("商品所属分类已停用");
    }

    @Test
    void purchasableViewHandlesMissingCategory() {
        when(dishMapper.selectById(101L)).thenReturn(dish(101L, 1L, EnableStatus.ENABLED));
        when(categoryService.byIds(any())).thenReturn(java.util.Map.of());

        var view = dishService.purchasable(101L);

        assertThat(view.available()).isFalse();
        assertThat(view.categoryId()).isNull();
        assertThat(view.categoryName()).isNull();
    }

    @Test
    void purchasableViewRejectsUnknownDish() {
        when(dishMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> dishService.purchasable(999L))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(CatalogErrorCode.DISH_NOT_FOUND));
    }

    @Test
    void purchasableByIdsReturnsBatchMap() {
        when(dishMapper.selectBatchIds(any())).thenReturn(List.of(
                dish(101L, 1L, EnableStatus.ENABLED), dish(102L, 1L, EnableStatus.ENABLED)));
        when(categoryService.byIds(any())).thenReturn(java.util.Map.of(1L, category(1L, CategoryType.DISH, EnableStatus.ENABLED)));

        var views = dishService.purchasableByIds(List.of(101L, 102L));

        assertThat(views).containsOnlyKeys(101L, 102L);
        assertThat(views.get(101L).available()).isTrue();
        verify(dishMapper, times(1)).selectBatchIds(any());
    }

    @Test
    void purchasableByIdsShortCircuitsOnEmptyInput() {
        assertThat(dishService.purchasableByIds(List.of())).isEmpty();
        assertThat(dishService.purchasableByIds(null)).isEmpty();
        verify(dishMapper, never()).selectBatchIds(any());
    }

    @Test
    void flavorOptionsReturnsDimensionToOptionsMap() {
        DishFlavor flavor = new DishFlavor();
        flavor.setName("辣度");
        flavor.setOptions(List.of("不辣", "微辣"));
        when(dishFlavorMapper.selectList(any())).thenReturn(List.of(flavor));

        var options = dishService.flavorOptions(101L);

        assertThat(options).containsOnlyKeys("辣度");
        assertThat(options.get("辣度")).containsExactly("不辣", "微辣");
    }
}
