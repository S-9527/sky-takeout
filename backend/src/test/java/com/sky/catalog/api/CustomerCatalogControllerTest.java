package com.sky.catalog.api;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import com.sky.catalog.domain.Category;
import com.sky.catalog.domain.CategoryType;
import com.sky.catalog.domain.Dish;
import com.sky.catalog.domain.DishFlavor;
import com.sky.catalog.domain.Setmeal;
import com.sky.catalog.service.CategoryService;
import com.sky.catalog.service.DishService;
import com.sky.catalog.service.SetmealService;
import com.sky.common.domain.EnableStatus;
import com.sky.common.domain.PageQuery;
import com.sky.common.domain.PageResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 顾客端目录:可见性过滤发生在 service,控制器只做映射,但映射错了同样会漏出不该看的字段。 */
class CustomerCatalogControllerTest {

    private final CategoryService categoryService = mock(CategoryService.class);
    private final DishService dishService = mock(DishService.class);
    private final SetmealService setmealService = mock(SetmealService.class);

    private final CustomerCatalogController controller =
            new CustomerCatalogController(categoryService, dishService, setmealService);

    private static Category category(long id, CategoryType type) {
        Category category = new Category();
        category.setId(id);
        category.setName(type == CategoryType.DISH ? "川湘菜" : "单人套餐");
        category.setType(type);
        category.setStatus(EnableStatus.ENABLED);
        return category;
    }

    private static Dish dish() {
        Dish dish = new Dish();
        dish.setId(101L);
        dish.setCategoryId(1L);
        dish.setName("宫保鸡丁");
        dish.setPriceCents(3800L);
        dish.setStatus(EnableStatus.ENABLED);
        dish.setSortOrder(1);
        return dish;
    }

    private static DishFlavor flavor() {
        DishFlavor flavor = new DishFlavor();
        flavor.setId(1L);
        flavor.setDishId(101L);
        flavor.setName("辣度");
        flavor.setOptions(List.of("不辣", "微辣"));
        return flavor;
    }

    private static Setmeal setmeal() {
        Setmeal setmeal = new Setmeal();
        setmeal.setId(201L);
        setmeal.setCategoryId(7L);
        setmeal.setName("单人川味套餐");
        setmeal.setPriceCents(4500L);
        setmeal.setStatus(EnableStatus.ENABLED);
        return setmeal;
    }

    @Test
    void categoriesNeverIncludeDisabledOnes() {
        when(categoryService.list(eq(CategoryType.DISH), anyBoolean()))
                .thenReturn(List.of(category(1L, CategoryType.DISH)));

        assertThat(controller.categories(CategoryType.DISH)).hasSize(1);
        verify(categoryService).list(CategoryType.DISH, false);
    }

    @Test
    void dishesListUsesCustomerVisiblePageAndFillsCategoryName() {
        when(dishService.pageCustomerVisible(eq(1L), any(PageQuery.class)))
                .thenReturn(PageResponse.of(List.of(dish()), 1, 20, 1));
        when(categoryService.namesByIds(any())).thenReturn(Map.of(1L, "川湘菜"));

        PageResponse<?> result = controller.dishes(1L, 1, 20);

        assertThat(result.records()).hasSize(1);
        verify(dishService).pageCustomerVisible(1L, PageQuery.of(1, 20));
    }

    @Test
    void dishDetailUsesCustomerVisibleLookupAndReturnsFlavors() {
        when(dishService.requireCustomerVisible(101L)).thenReturn(dish());
        when(dishService.flavorsOf(101L)).thenReturn(List.of(flavor()));
        when(categoryService.requireById(1L)).thenReturn(category(1L, CategoryType.DISH));

        var response = controller.dish(101L);

        assertThat(response.id()).isEqualTo(101L);
        assertThat(response.categoryName()).isEqualTo("川湘菜");
        assertThat(response.flavors()).hasSize(1);
        assertThat(response.flavors().get(0).options()).containsExactly("不辣", "微辣");
    }

    @Test
    void setmealsListPassesOptionalCategoryFilterThrough() {
        when(setmealService.pageCustomerVisible(eq(null), any(PageQuery.class)))
                .thenReturn(PageResponse.of(List.of(setmeal()), 1, 20, 1));
        when(categoryService.namesByIds(any())).thenReturn(Map.of(7L, "单人套餐"));

        PageResponse<?> result = controller.setmeals(null, 1, 20);

        assertThat(result.records()).hasSize(1);
        verify(setmealService).pageCustomerVisible(null, PageQuery.of(1, 20));
    }

    @Test
    void setmealDetailReturnsItemsWithDishNames() {
        when(setmealService.requireCustomerVisible(201L)).thenReturn(setmeal());
        when(setmealService.itemsOf(201L))
                .thenReturn(List.of(new SetmealService.ItemView(1L, 101L, "宫保鸡丁", 3800L, 1)));
        when(categoryService.requireById(7L)).thenReturn(category(7L, CategoryType.SETMEAL));

        var response = controller.setmeal(201L);

        assertThat(response.id()).isEqualTo(201L);
        assertThat(response.categoryName()).isEqualTo("单人套餐");
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).dishName()).isEqualTo("宫保鸡丁");
    }
}
