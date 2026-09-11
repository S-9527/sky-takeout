package com.sky.catalog.api;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import com.sky.catalog.api.dto.CategoryCreateRequest;
import com.sky.catalog.api.dto.CategoryResponse;
import com.sky.catalog.api.dto.CategoryUpdateRequest;
import com.sky.catalog.domain.Category;
import com.sky.catalog.domain.CategoryType;
import com.sky.catalog.service.CategoryService;
import com.sky.common.domain.EnableStatus;
import com.sky.common.domain.PageResponse;
import com.sky.common.error.BusinessException;
import com.sky.common.error.CommonErrorCode;
import com.sky.common.web.StatusPatchRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CategoryControllerTest {

    private final CategoryService categoryService = mock(CategoryService.class);
    private final CategoryController controller = new CategoryController(categoryService);
    private final CustomerCatalogController customerController = new CustomerCatalogController(categoryService);

    private static Category category() {
        Category category = new Category();
        category.setId(1L);
        category.setName("川湘菜");
        category.setType(CategoryType.DISH);
        category.setSortOrder(1);
        category.setStatus(EnableStatus.ENABLED);
        category.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        category.setUpdatedAt(LocalDateTime.of(2026, 1, 2, 0, 0));
        return category;
    }

    @Test
    void pageUsesDefaultSortAndMapsToDto() {
        when(categoryService.page(any(), any(), any(), any(), any()))
                .thenReturn(PageResponse.of(List.of(category()), 1, 20, 1));

        PageResponse<CategoryResponse> result = controller.page(1, 20, null, "川", CategoryType.DISH, 1);

        assertThat(result.records()).hasSize(1);
        assertThat(result.records().get(0).type()).isEqualTo("DISH");
        assertThat(result.records().get(0).status()).isEqualTo(1);
        assertThat(result.records().get(0).createdAt()).isNotNull();
    }

    @Test
    void pageRejectsSortFieldOutsideWhitelist() {
        assertThatThrownBy(() -> controller.page(1, 20, "passwordHash,asc", null, null, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(CommonErrorCode.COMMON_SORT_FIELD_NOT_ALLOWED));
    }

    @Test
    void createPassesEveryFieldToService() {
        when(categoryService.create(any(), any(), any(), any())).thenReturn(category());

        CategoryResponse response = controller.create(
                new CategoryCreateRequest("川湘菜", CategoryType.DISH, 1, 1));

        assertThat(response.name()).isEqualTo("川湘菜");
        verify(categoryService).create("川湘菜", CategoryType.DISH, 1, 1);
    }

    @Test
    void optionsDefaultToEnabledOnly() {
        when(categoryService.list(CategoryType.DISH, false)).thenReturn(List.of(category()));

        assertThat(controller.options(CategoryType.DISH, false)).hasSize(1);
        verify(categoryService).list(CategoryType.DISH, false);
    }

    @Test
    void optionsCanIncludeDisabled() {
        when(categoryService.list(CategoryType.SETMEAL, true)).thenReturn(List.of(category()));

        assertThat(controller.options(CategoryType.SETMEAL, true)).hasSize(1);
    }

    @Test
    void getByIdMapsCategory() {
        when(categoryService.requireById(1L)).thenReturn(category());

        assertThat(controller.getById(1L).id()).isEqualTo(1L);
    }

    @Test
    void updatePassesOptionalTypeThrough() {
        when(categoryService.update(anyLong(), any(), any(), any(), any())).thenReturn(category());

        controller.update(1L, new CategoryUpdateRequest("川湘菜", CategoryType.DISH, 2, 1));

        verify(categoryService).update(1L, "川湘菜", CategoryType.DISH, 2, 1);
    }

    @Test
    void deleteDelegates() {
        controller.delete(11L);

        verify(categoryService).delete(11L);
    }

    @Test
    void changeStatusMapsIntegerToEnum() {
        controller.changeStatus(1L, new StatusPatchRequest(0));

        verify(categoryService).setStatus(1L, EnableStatus.DISABLED);
    }

    @Test
    void customerCategoriesNeverIncludeDisabledOnes() {
        when(categoryService.list(eq(CategoryType.DISH), anyBoolean())).thenReturn(List.of(category()));

        List<CategoryResponse> result = customerController.categories(CategoryType.DISH);

        assertThat(result).hasSize(1);
        verify(categoryService).list(CategoryType.DISH, false);
    }
}
