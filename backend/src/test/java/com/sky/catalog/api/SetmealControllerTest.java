package com.sky.catalog.api;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import com.sky.catalog.api.dto.BatchStatusRequest;
import com.sky.catalog.api.dto.SetmealCreateRequest;
import com.sky.catalog.api.dto.SetmealDetailResponse;
import com.sky.catalog.api.dto.SetmealItemRequest;
import com.sky.catalog.api.dto.SetmealUpdateRequest;
import com.sky.catalog.domain.Category;
import com.sky.catalog.domain.CategoryType;
import com.sky.catalog.domain.Setmeal;
import com.sky.catalog.service.CategoryService;
import com.sky.catalog.service.SetmealService;
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

class SetmealControllerTest {

    private final SetmealService setmealService = mock(SetmealService.class);
    private final CategoryService categoryService = mock(CategoryService.class);
    private final SetmealController controller = new SetmealController(setmealService, categoryService);

    private static Setmeal setmeal() {
        Setmeal setmeal = new Setmeal();
        setmeal.setId(201L);
        setmeal.setCategoryId(7L);
        setmeal.setName("单人川味套餐");
        setmeal.setPriceCents(4500L);
        setmeal.setStatus(EnableStatus.ENABLED);
        return setmeal;
    }

    private static Category category() {
        Category category = new Category();
        category.setId(7L);
        category.setName("单人套餐");
        category.setType(CategoryType.SETMEAL);
        category.setStatus(EnableStatus.ENABLED);
        return category;
    }

    private static SetmealService.ItemView itemView() {
        return new SetmealService.ItemView(1L, 101L, "宫保鸡丁", 3800L, 1);
    }

    @Test
    void pageFillsCategoryNameFromBatchLookup() {
        when(setmealService.page(any(), any(), any(), any(), any()))
                .thenReturn(PageResponse.of(List.of(setmeal()), 1, 20, 1));
        when(categoryService.namesByIds(any())).thenReturn(Map.of(7L, "单人套餐"));

        PageResponse<?> result = controller.page(1, 20, null, "川味", 7L, 1);

        assertThat(result.records()).hasSize(1);
    }

    @Test
    void pageRejectsSortFieldOutsideWhitelist() {
        assertThatThrownBy(() -> controller.page(1, 20, "sortOrder,asc", null, null, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(CommonErrorCode.COMMON_SORT_FIELD_NOT_ALLOWED));
    }

    @Test
    void createMapsItemsAndReturnsDetail() {
        when(setmealService.create(any(), any(), any(), any(), any(), any(), anyList())).thenReturn(setmeal());
        when(setmealService.itemsOf(201L)).thenReturn(List.of(itemView()));
        when(categoryService.requireById(7L)).thenReturn(category());

        SetmealDetailResponse response = controller.create(new SetmealCreateRequest(
                7L, "单人川味套餐", 4500L, "/files/setmeal/x.jpg", "描述", 1,
                List.of(new SetmealItemRequest(101L, 1))));

        assertThat(response.name()).isEqualTo("单人川味套餐");
        assertThat(response.categoryName()).isEqualTo("单人套餐");
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).dishName()).isEqualTo("宫保鸡丁");
        assertThat(response.items().get(0).dishPriceCents()).isEqualTo(3800L);

        verify(setmealService).create(7L, "单人川味套餐", 4500L, "/files/setmeal/x.jpg", "描述", 1,
                List.of(new SetmealService.ItemInput(101L, 1)));
    }

    @Test
    void createWithoutItemsPassesNull() {
        when(setmealService.create(any(), any(), any(), any(), any(), any(), any())).thenReturn(setmeal());
        when(setmealService.itemsOf(201L)).thenReturn(List.of());
        when(categoryService.requireById(7L)).thenReturn(category());

        controller.create(new SetmealCreateRequest(7L, "单人川味套餐", 4500L, null, null, null, null));

        verify(setmealService).create(7L, "单人川味套餐", 4500L, null, null, null, null);
    }

    @Test
    void getByIdReturnsDetailWithItems() {
        when(setmealService.requireById(201L)).thenReturn(setmeal());
        when(setmealService.itemsOf(201L)).thenReturn(List.of(itemView()));
        when(categoryService.requireById(7L)).thenReturn(category());

        SetmealDetailResponse response = controller.getById(201L);

        assertThat(response.id()).isEqualTo(201L);
        assertThat(response.status()).isEqualTo(1);
        assertThat(response.items().get(0).copies()).isEqualTo(1);
    }

    @Test
    void updatePassesItemsThrough() {
        when(setmealService.update(anyLong(), any(), any(), any(), any(), any(), any(), anyList())).thenReturn(setmeal());
        when(setmealService.itemsOf(201L)).thenReturn(List.of(itemView()));
        when(categoryService.requireById(7L)).thenReturn(category());

        controller.update(201L, new SetmealUpdateRequest(
                7L, "单人川味套餐", 4400L, null, null, 1, List.of(new SetmealItemRequest(101L, 2))));

        verify(setmealService).update(201L, 7L, "单人川味套餐", 4400L, null, null, 1,
                List.of(new SetmealService.ItemInput(101L, 2)));
    }

    @Test
    void deleteParsesCommaSeparatedIds() {
        controller.delete("201,202,201");

        verify(setmealService).delete(List.of(201L, 202L));
    }

    @Test
    void deleteRejectsMalformedIds() {
        assertThatThrownBy(() -> controller.delete("201,x"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(CommonErrorCode.COMMON_VALIDATION_FAILED));
    }

    @Test
    void changeStatusMapsIntegerToEnum() {
        controller.changeStatus(new BatchStatusRequest(List.of(201L), 1));

        verify(setmealService).changeStatus(List.of(201L), EnableStatus.ENABLED);
    }
}
