package com.sky.catalog.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;

import com.sky.catalog.domain.CatalogErrorCode;
import com.sky.catalog.domain.Category;
import com.sky.catalog.domain.CategoryType;
import com.sky.catalog.mapper.CategoryMapper;
import com.sky.common.domain.EnableStatus;
import com.sky.common.domain.PageQuery;
import com.sky.common.domain.PageResponse;
import com.sky.common.domain.SortSpec;
import com.sky.common.error.BusinessException;
import com.sky.common.error.CommonErrorCode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CategoryServiceTest {

    private final CategoryMapper categoryMapper = mock(CategoryMapper.class);
    private final CategoryService categoryService = new CategoryService(categoryMapper);

    private static Category category(long id, String name, CategoryType type, int sortOrder) {
        Category category = new Category();
        category.setId(id);
        category.setName(name);
        category.setType(type);
        category.setSortOrder(sortOrder);
        category.setStatus(EnableStatus.ENABLED);
        return category;
    }

    // ---------------------------------------------------------------- 分页

    @Test
    void pageAppliesFiltersAndReturnsMetadata() {
        Page<Category> page = new Page<>(1, 20);
        page.setRecords(List.of(category(1L, "川湘菜", CategoryType.DISH, 1)));
        page.setTotal(10);
        when(categoryMapper.selectPage(any(), any())).thenReturn(page);

        PageResponse<Category> result = categoryService.page(
                "川", CategoryType.DISH, 1, PageQuery.of(1, 20),
                SortSpec.parse(null, CategoryService.SORT_WHITELIST, "sortOrder", false));

        assertThat(result.records()).hasSize(1);
        assertThat(result.total()).isEqualTo(10);
        assertThat(result.pageSize()).isEqualTo(20);
    }

    @Test
    void pageRejectsSortFieldOutsideWhitelist() {
        assertThatThrownBy(() -> categoryService.page(
                null, null, null, PageQuery.of(1, 20), new SortSpec("id", false)))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(CommonErrorCode.COMMON_SORT_FIELD_NOT_ALLOWED));
    }

    @Test
    void pageSupportsEveryWhitelistedSortFieldInBothDirections() {
        Page<Category> page = new Page<>(1, 20);
        page.setRecords(List.of());
        when(categoryMapper.selectPage(any(), any())).thenReturn(page);

        for (String field : CategoryService.SORT_WHITELIST) {
            categoryService.page(null, null, null, PageQuery.of(1, 20), new SortSpec(field, true));
            categoryService.page(null, null, null, PageQuery.of(1, 20), new SortSpec(field, false));
        }

        assertThat(CategoryService.SORT_WHITELIST)
                .containsExactlyInAnyOrder("sortOrder", "name", "createdAt");
    }

    /** status=2 是"看不懂的取值",必须 400,而不是被当成"禁用"静默纠正。 */
    @Test
    void pageRejectsIllegalStatusValue() {
        assertThatThrownBy(() -> categoryService.page(
                null, null, 2, PageQuery.of(1, 20), new SortSpec("sortOrder", false)))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(CommonErrorCode.COMMON_VALIDATION_FAILED));
    }

    // ---------------------------------------------------------------- 下拉列表

    @Test
    void listWithoutDisabledOnlyReturnsEnabled() {
        when(categoryMapper.selectList(any())).thenReturn(List.of(category(1L, "川湘菜", CategoryType.DISH, 1)));

        List<Category> result = categoryService.list(CategoryType.DISH, false);

        assertThat(result).hasSize(1);
        verify(categoryMapper).selectList(any());
    }

    @Test
    void listCanIncludeDisabledForAdminEditing() {
        when(categoryMapper.selectList(any())).thenReturn(List.of(
                category(1L, "川湘菜", CategoryType.DISH, 1),
                category(2L, "停用分类", CategoryType.DISH, 2)));

        assertThat(categoryService.list(CategoryType.DISH, true)).hasSize(2);
    }

    // ---------------------------------------------------------------- 查询

    @Test
    void requireByIdReturnsCategoryOr404() {
        when(categoryMapper.selectById(1L)).thenReturn(category(1L, "川湘菜", CategoryType.DISH, 1));
        assertThat(categoryService.requireById(1L).getName()).isEqualTo("川湘菜");

        when(categoryMapper.selectById(404L)).thenReturn(null);
        assertThatThrownBy(() -> categoryService.requireById(404L))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(CatalogErrorCode.CATEGORY_NOT_FOUND));
    }

    // ---------------------------------------------------------------- 新增

    @Test
    void createDefaultsSortOrderToZeroAndStatusToEnabled() {
        when(categoryMapper.exists(any())).thenReturn(false);
        when(categoryMapper.selectById(any())).thenReturn(category(11L, "新分类", CategoryType.DISH, 0));

        categoryService.create("新分类", CategoryType.DISH, null, null);

        ArgumentCaptor<Category> inserted = ArgumentCaptor.forClass(Category.class);
        verify(categoryMapper).insert(inserted.capture());
        assertThat(inserted.getValue().getSortOrder()).isZero();
        assertThat(inserted.getValue().getStatus()).isEqualTo(EnableStatus.ENABLED);
    }

    @Test
    void createHonoursExplicitSortOrderAndStatus() {
        when(categoryMapper.exists(any())).thenReturn(false);
        when(categoryMapper.selectById(any())).thenReturn(category(11L, "新分类", CategoryType.DISH, 5));

        categoryService.create("新分类", CategoryType.DISH, 5, 0);

        ArgumentCaptor<Category> inserted = ArgumentCaptor.forClass(Category.class);
        verify(categoryMapper).insert(inserted.capture());
        assertThat(inserted.getValue().getSortOrder()).isEqualTo(5);
        assertThat(inserted.getValue().getStatus()).isEqualTo(EnableStatus.DISABLED);
    }

    @Test
    void createRejectsDuplicateNameWithinSameType() {
        when(categoryMapper.exists(any())).thenReturn(true);

        assertThatThrownBy(() -> categoryService.create("川湘菜", CategoryType.DISH, 1, 1))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(CatalogErrorCode.CATEGORY_NAME_TAKEN));
        verify(categoryMapper, never()).insert(any(Category.class));
    }

    @Test
    void createRejectsIllegalStatusValue() {
        when(categoryMapper.exists(any())).thenReturn(false);

        assertThatThrownBy(() -> categoryService.create("新分类", CategoryType.DISH, 0, 7))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(CommonErrorCode.COMMON_VALIDATION_FAILED));
        verify(categoryMapper, never()).insert(any(Category.class));
    }

    // ---------------------------------------------------------------- 编辑

    @Test
    void updateKeepsTypeWhenRequestOmitsIt() {
        when(categoryMapper.selectById(1L)).thenReturn(category(1L, "川湘菜", CategoryType.DISH, 1));
        when(categoryMapper.exists(any())).thenReturn(false);

        categoryService.update(1L, "川湘菜(新)", null, 3, 1);

        ArgumentCaptor<Category> update = ArgumentCaptor.forClass(Category.class);
        verify(categoryMapper).updateById(update.capture());
        assertThat(update.getValue().getType()).isNull();
        assertThat(update.getValue().getSortOrder()).isEqualTo(3);
    }

    @Test
    void updateAcceptsTypeWhenItMatchesCurrentOne() {
        when(categoryMapper.selectById(1L)).thenReturn(category(1L, "川湘菜", CategoryType.DISH, 1));
        when(categoryMapper.exists(any())).thenReturn(false);

        categoryService.update(1L, "川湘菜", CategoryType.DISH, 1, 1);

        verify(categoryMapper).updateById(any(Category.class));
    }

    /** 试图改类型必须明确报错:静默忽略会让前端以为改成功了。 */
    @Test
    void updateRejectsTypeChange() {
        when(categoryMapper.selectById(1L)).thenReturn(category(1L, "川湘菜", CategoryType.DISH, 1));

        assertThatThrownBy(() -> categoryService.update(1L, "川湘菜", CategoryType.SETMEAL, 1, 1))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(CatalogErrorCode.CATEGORY_TYPE_IMMUTABLE));
        verify(categoryMapper, never()).updateById(any(Category.class));
    }

    @Test
    void updateRejectsNameTakenByAnotherCategoryOfSameType() {
        when(categoryMapper.selectById(1L)).thenReturn(category(1L, "川湘菜", CategoryType.DISH, 1));
        when(categoryMapper.exists(any())).thenReturn(true);

        assertThatThrownBy(() -> categoryService.update(1L, "家常菜", null, 1, 1))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(CatalogErrorCode.CATEGORY_NAME_TAKEN));
        verify(categoryMapper, never()).updateById(any(Category.class));
    }

    @Test
    void updateRejectsUnknownCategory() {
        when(categoryMapper.selectById(404L)).thenReturn(null);

        assertThatThrownBy(() -> categoryService.update(404L, "任意", null, 1, 1))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(CatalogErrorCode.CATEGORY_NOT_FOUND));
    }

    // ---------------------------------------------------------------- 删除与状态

    @Test
    void deleteRemovesCategory() {
        when(categoryMapper.selectById(11L)).thenReturn(category(11L, "空分类", CategoryType.DISH, 9));

        categoryService.delete(11L);

        verify(categoryMapper).deleteById(11L);
    }

    /** 被菜品/套餐引用时数据库外键会拒绝删除,必须翻译成 422 而不是 500。 */
    @Test
    void deleteTranslatesForeignKeyViolationIntoCategoryInUse() {
        when(categoryMapper.selectById(1L)).thenReturn(category(1L, "川湘菜", CategoryType.DISH, 1));
        when(categoryMapper.deleteById(1L))
                .thenThrow(new DataIntegrityViolationException("Cannot delete or update a parent row"));

        assertThatThrownBy(() -> categoryService.delete(1L))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(CatalogErrorCode.CATEGORY_IN_USE));
    }

    @Test
    void deleteRejectsUnknownCategory() {
        when(categoryMapper.selectById(404L)).thenReturn(null);

        assertThatThrownBy(() -> categoryService.delete(404L))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(CatalogErrorCode.CATEGORY_NOT_FOUND));
        verify(categoryMapper, never()).deleteById(any(Long.class));
    }

    @Test
    void setStatusUpdatesOnlyStatus() {
        when(categoryMapper.selectById(2L)).thenReturn(category(2L, "家常菜", CategoryType.DISH, 2));

        categoryService.setStatus(2L, EnableStatus.DISABLED);

        ArgumentCaptor<Category> update = ArgumentCaptor.forClass(Category.class);
        verify(categoryMapper).updateById(update.capture());
        assertThat(update.getValue().getStatus()).isEqualTo(EnableStatus.DISABLED);
        assertThat(update.getValue().getName()).isNull();
    }

    @Test
    void setStatusRejectsUnknownCategory() {
        when(categoryMapper.selectById(404L)).thenReturn(null);

        assertThatThrownBy(() -> categoryService.setStatus(404L, EnableStatus.DISABLED))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(CatalogErrorCode.CATEGORY_NOT_FOUND));
    }
}
