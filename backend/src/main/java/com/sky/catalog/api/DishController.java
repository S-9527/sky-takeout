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
import java.util.Map;

import com.sky.catalog.api.dto.BatchStatusRequest;
import com.sky.catalog.api.dto.DishCreateRequest;
import com.sky.catalog.api.dto.DishDetailResponse;
import com.sky.catalog.api.dto.DishFlavorRequest;
import com.sky.catalog.api.dto.DishFlavorResponse;
import com.sky.catalog.api.dto.DishResponse;
import com.sky.catalog.api.dto.DishUpdateRequest;
import com.sky.catalog.domain.Dish;
import com.sky.catalog.service.CategoryService;
import com.sky.catalog.service.DishService;
import com.sky.common.domain.EnableStatus;
import com.sky.common.domain.PageQuery;
import com.sky.common.domain.PageResponse;
import com.sky.common.domain.SortSpec;
import com.sky.common.web.IdsParam;

/** 管理端菜品管理。整个前缀由 SecurityConfig 限制为 ADMIN / STAFF。 */
@RestController
@RequestMapping("/api/v1/admin/dishes")
public class DishController {

    private final DishService dishService;
    private final CategoryService categoryService;

    public DishController(DishService dishService, CategoryService categoryService) {
        this.dishService = dishService;
        this.categoryService = categoryService;
    }

    @GetMapping
    public PageResponse<DishResponse> page(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Integer status) {
        SortSpec sortSpec = SortSpec.parse(sort, DishService.SORT_WHITELIST, "sortOrder", false);
        PageResponse<Dish> result = dishService.page(
                name, categoryId, status, PageQuery.of(page, pageSize), sortSpec);
        // 分类名一次性批量取,避免每行一次查询(N+1)
        Map<Long, String> categoryNames = categoryService.namesByIds(
                result.records().stream().map(Dish::getCategoryId).distinct().toList());
        return result.map(dish -> DishResponse.from(dish, categoryNames.get(dish.getCategoryId())));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DishDetailResponse create(@Valid @RequestBody DishCreateRequest request) {
        Dish created = dishService.create(
                request.categoryId(), request.name(), request.priceCents(), request.imageUrl(),
                request.description(), request.status(), request.sortOrder(),
                toFlavorInputs(request.flavors()));
        return detail(created);
    }

    /** 批量删除,{@code ?ids=1,2}。 */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestParam("ids") String ids) {
        dishService.delete(IdsParam.parse(ids));
    }

    @PatchMapping("/status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changeStatus(@Valid @RequestBody BatchStatusRequest request) {
        dishService.changeStatus(request.ids(), EnableStatus.of(request.status()));
    }

    @GetMapping("/{id}")
    public DishDetailResponse getById(@PathVariable Long id) {
        return detail(dishService.requireById(id));
    }

    @PutMapping("/{id}")
    public DishDetailResponse update(@PathVariable Long id, @Valid @RequestBody DishUpdateRequest request) {
        Dish updated = dishService.update(
                id, request.categoryId(), request.name(), request.priceCents(), request.imageUrl(),
                request.description(), request.status(), request.sortOrder(),
                toFlavorInputs(request.flavors()));
        return detail(updated);
    }

    private DishDetailResponse detail(Dish dish) {
        String categoryName = categoryService.requireById(dish.getCategoryId()).getName();
        List<DishFlavorResponse> flavors = dishService.flavorsOf(dish.getId()).stream()
                .map(DishFlavorResponse::from)
                .toList();
        return DishDetailResponse.from(dish, categoryName, flavors);
    }

    private static List<DishService.FlavorInput> toFlavorInputs(List<DishFlavorRequest> flavors) {
        return flavors == null
                ? null
                : flavors.stream()
                        .map(flavor -> new DishService.FlavorInput(flavor.name(), flavor.options(), flavor.sortOrder()))
                        .toList();
    }
}
