package com.sky.catalog.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import com.sky.catalog.api.dto.CategoryCreateRequest;
import com.sky.catalog.api.dto.CategoryResponse;
import com.sky.catalog.api.dto.CategoryUpdateRequest;
import com.sky.catalog.domain.Category;
import com.sky.catalog.domain.CategoryType;
import com.sky.catalog.service.CategoryService;
import com.sky.common.domain.EnableStatus;
import com.sky.common.domain.PageQuery;
import com.sky.common.domain.PageResponse;
import com.sky.common.domain.SortSpec;
import com.sky.common.web.StatusPatchRequest;

/** 管理端分类管理。整个前缀由 SecurityConfig 限制为 ADMIN / STAFF。 */
@RestController
@RequestMapping("/api/v1/admin/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public PageResponse<CategoryResponse> page(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) CategoryType type,
            @RequestParam(required = false) Integer status) {
        SortSpec sortSpec = SortSpec.parse(sort, CategoryService.SORT_WHITELIST, "sortOrder", false);
        PageResponse<Category> result = categoryService.page(
                name, type, status, PageQuery.of(page, pageSize), sortSpec);
        return result.map(CategoryResponse::from);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse create(@Valid @RequestBody CategoryCreateRequest request) {
        Category created = categoryService.create(
                request.name(), request.type(), request.sortOrder(), request.status());
        return CategoryResponse.from(created);
    }

    /** 下拉用,不分页。默认只返回启用中的分类;编辑旧商品时可以带 {@code includeDisabled=true}。 */
    @GetMapping("/options")
    public List<CategoryResponse> options(@RequestParam CategoryType type,
                                          @RequestParam(required = false, defaultValue = "false") boolean includeDisabled) {
        return categoryService.list(type, includeDisabled).stream().map(CategoryResponse::from).toList();
    }

    @GetMapping("/{id}")
    public CategoryResponse getById(@PathVariable Long id) {
        return CategoryResponse.from(categoryService.requireById(id));
    }

    @PutMapping("/{id}")
    public CategoryResponse update(@PathVariable Long id, @Valid @RequestBody CategoryUpdateRequest request) {
        Category updated = categoryService.update(
                id, request.name(), request.type(), request.sortOrder(), request.status());
        return CategoryResponse.from(updated);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        categoryService.delete(id);
    }

    @PatchMapping("/{id}/status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changeStatus(@PathVariable Long id, @Valid @RequestBody StatusPatchRequest request) {
        categoryService.setStatus(id, EnableStatus.of(request.status()));
    }
}
