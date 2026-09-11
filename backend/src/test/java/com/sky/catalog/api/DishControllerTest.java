package com.sky.catalog.api;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import com.sky.catalog.api.dto.BatchStatusRequest;
import com.sky.catalog.api.dto.DishCreateRequest;
import com.sky.catalog.api.dto.DishDetailResponse;
import com.sky.catalog.api.dto.DishFlavorRequest;
import com.sky.catalog.api.dto.DishUpdateRequest;
import com.sky.catalog.domain.Category;
import com.sky.catalog.domain.CategoryType;
import com.sky.catalog.domain.Dish;
import com.sky.catalog.domain.DishFlavor;
import com.sky.catalog.service.CategoryService;
import com.sky.catalog.service.DishService;
import com.sky.common.domain.EnableStatus;
import com.sky.common.domain.PageResponse;
import com.sky.common.error.BusinessException;
import com.sky.common.error.CommonErrorCode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DishControllerTest {

    private final DishService dishService = mock(DishService.class);
    private final CategoryService categoryService = mock(CategoryService.class);
    private final DishController controller = new DishController(dishService, categoryService);

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

    private static Category category() {
        Category category = new Category();
        category.setId(1L);
        category.setName("川湘菜");
        category.setType(CategoryType.DISH);
        category.setStatus(EnableStatus.ENABLED);
        return category;
    }

    private static DishFlavor flavor() {
        DishFlavor flavor = new DishFlavor();
        flavor.setId(1L);
        flavor.setDishId(101L);
        flavor.setName("辣度");
        flavor.setOptions(List.of("不辣", "微辣"));
        flavor.setSortOrder(1);
        return flavor;
    }

    @Test
    void pageFillsCategoryNameFromBatchLookup() {
        when(dishService.page(any(), any(), any(), any(), any()))
                .thenReturn(PageResponse.of(List.of(dish()), 1, 20, 1));
        when(categoryService.namesByIds(any())).thenReturn(Map.of(1L, "川湘菜"));

        PageResponse<?> result = controller.page(1, 20, null, "宫保", 1L, 1);

        assertThat(result.records()).hasSize(1);
    }

    @Test
    void pageRejectsSortFieldOutsideWhitelist() {
        assertThatThrownBy(() -> controller.page(1, 20, "id,asc", null, null, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(CommonErrorCode.COMMON_SORT_FIELD_NOT_ALLOWED));
    }

    @Test
    void createMapsFlavorDtosToServiceInputs() {
        when(dishService.create(any(), any(), any(), any(), any(), any(), any(), anyList())).thenReturn(dish());
        when(dishService.flavorsOf(101L)).thenReturn(List.of(flavor()));
        when(categoryService.requireById(1L)).thenReturn(category());

        DishDetailResponse response = controller.create(new DishCreateRequest(
                1L, "宫保鸡丁", 3800L, "/files/dish/x.jpg", "香辣", 1, 2,
                List.of(new DishFlavorRequest("辣度", List.of("不辣", "微辣"), 1))));

        assertThat(response.name()).isEqualTo("宫保鸡丁");
        assertThat(response.categoryName()).isEqualTo("川湘菜");
        assertThat(response.flavors()).hasSize(1);
        assertThat(response.flavors().get(0).options()).containsExactly("不辣", "微辣");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DishService.FlavorInput>> flavors = ArgumentCaptor.forClass(List.class);
        verify(dishService).create(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq("宫保鸡丁"),
                org.mockito.ArgumentMatchers.eq(3800L), org.mockito.ArgumentMatchers.eq("/files/dish/x.jpg"),
                org.mockito.ArgumentMatchers.eq("香辣"), org.mockito.ArgumentMatchers.eq(1),
                org.mockito.ArgumentMatchers.eq(2), flavors.capture());
        assertThat(flavors.getValue()).containsExactly(new DishService.FlavorInput("辣度", List.of("不辣", "微辣"), 1));
    }

    /** 请求体里没有 flavors 时必须是 null(不改配置),而不是空列表(清空配置)。 */
    @Test
    void createWithoutFlavorsPassesNull() {
        when(dishService.create(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(dish());
        when(dishService.flavorsOf(101L)).thenReturn(List.of());
        when(categoryService.requireById(1L)).thenReturn(category());

        controller.create(new DishCreateRequest(1L, "宫保鸡丁", 3800L, null, null, null, null, null));

        verify(dishService).create(1L, "宫保鸡丁", 3800L, null, null, null, null, null);
    }

    @Test
    void getByIdReturnsDetailWithFlavors() {
        when(dishService.requireById(101L)).thenReturn(dish());
        when(dishService.flavorsOf(101L)).thenReturn(List.of(flavor()));
        when(categoryService.requireById(1L)).thenReturn(category());

        DishDetailResponse response = controller.getById(101L);

        assertThat(response.id()).isEqualTo(101L);
        assertThat(response.status()).isEqualTo(1);
        assertThat(response.flavors().get(0).name()).isEqualTo("辣度");
    }

    @Test
    void updatePassesFieldsAndNullFlavors() {
        when(dishService.update(anyLong(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(dish());
        when(dishService.flavorsOf(101L)).thenReturn(List.of());
        when(categoryService.requireById(1L)).thenReturn(category());

        controller.update(101L, new DishUpdateRequest(1L, "宫保鸡丁", 4200L, null, null, 1, 3, null));

        verify(dishService).update(101L, 1L, "宫保鸡丁", 4200L, null, null, 1, 3, null);
    }

    @Test
    void updateWithExplicitEmptyFlavorListPassesEmptyList() {
        when(dishService.update(anyLong(), any(), any(), any(), any(), any(), any(), any(), anyList())).thenReturn(dish());
        when(dishService.flavorsOf(101L)).thenReturn(List.of());
        when(categoryService.requireById(1L)).thenReturn(category());

        controller.update(101L, new DishUpdateRequest(1L, "宫保鸡丁", 4200L, null, null, 1, 3, List.of()));

        verify(dishService).update(101L, 1L, "宫保鸡丁", 4200L, null, null, 1, 3, List.of());
    }

    @Test
    void deleteParsesCommaSeparatedIds() {
        controller.delete("101,102,101");

        verify(dishService).delete(List.of(101L, 102L));
    }

    @Test
    void deleteRejectsMalformedIds() {
        assertThatThrownBy(() -> controller.delete("101,abc"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(CommonErrorCode.COMMON_VALIDATION_FAILED));
    }

    @Test
    void changeStatusMapsIntegerToEnum() {
        controller.changeStatus(new BatchStatusRequest(List.of(101L, 102L), 0));

        verify(dishService).changeStatus(List.of(101L, 102L), EnableStatus.DISABLED);
    }
}
