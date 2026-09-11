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
import com.sky.catalog.api.dto.SetmealCreateRequest;
import com.sky.catalog.api.dto.SetmealDetailResponse;
import com.sky.catalog.api.dto.SetmealItemRequest;
import com.sky.catalog.api.dto.SetmealItemResponse;
import com.sky.catalog.api.dto.SetmealResponse;
import com.sky.catalog.api.dto.SetmealUpdateRequest;
import com.sky.catalog.domain.Setmeal;
import com.sky.catalog.service.CategoryService;
import com.sky.catalog.service.SetmealService;
import com.sky.common.domain.EnableStatus;
import com.sky.common.domain.PageQuery;
import com.sky.common.domain.PageResponse;
import com.sky.common.domain.SortSpec;
import com.sky.common.web.IdsParam;

/** 管理端套餐管理。整个前缀由 SecurityConfig 限制为 ADMIN / STAFF。 */
@RestController
@RequestMapping("/api/v1/admin/setmeals")
public class SetmealController {

    private final SetmealService setmealService;
    private final CategoryService categoryService;

    public SetmealController(SetmealService setmealService, CategoryService categoryService) {
        this.setmealService = setmealService;
        this.categoryService = categoryService;
    }

    @GetMapping
    public PageResponse<SetmealResponse> page(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Integer status) {
        SortSpec sortSpec = SortSpec.parse(sort, SetmealService.SORT_WHITELIST, "createdAt", true);
        PageResponse<Setmeal> result = setmealService.page(
                name, categoryId, status, PageQuery.of(page, pageSize), sortSpec);
        Map<Long, String> categoryNames = categoryService.namesByIds(
                result.records().stream().map(Setmeal::getCategoryId).distinct().toList());
        return result.map(setmeal -> SetmealResponse.from(setmeal, categoryNames.get(setmeal.getCategoryId())));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SetmealDetailResponse create(@Valid @RequestBody SetmealCreateRequest request) {
        Setmeal created = setmealService.create(
                request.categoryId(), request.name(), request.priceCents(), request.imageUrl(),
                request.description(), request.status(), toItemInputs(request.items()));
        return detail(created);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestParam("ids") String ids) {
        setmealService.delete(IdsParam.parse(ids));
    }

    @PatchMapping("/status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changeStatus(@Valid @RequestBody BatchStatusRequest request) {
        setmealService.changeStatus(request.ids(), EnableStatus.of(request.status()));
    }

    @GetMapping("/{id}")
    public SetmealDetailResponse getById(@PathVariable Long id) {
        return detail(setmealService.requireById(id));
    }

    @PutMapping("/{id}")
    public SetmealDetailResponse update(@PathVariable Long id, @Valid @RequestBody SetmealUpdateRequest request) {
        Setmeal updated = setmealService.update(
                id, request.categoryId(), request.name(), request.priceCents(), request.imageUrl(),
                request.description(), request.status(), toItemInputs(request.items()));
        return detail(updated);
    }

    private SetmealDetailResponse detail(Setmeal setmeal) {
        String categoryName = categoryService.requireById(setmeal.getCategoryId()).getName();
        List<SetmealItemResponse> items = setmealService.itemsOf(setmeal.getId()).stream()
                .map(SetmealItemResponse::from)
                .toList();
        return SetmealDetailResponse.from(setmeal, categoryName, items);
    }

    private static List<SetmealService.ItemInput> toItemInputs(List<SetmealItemRequest> items) {
        return items == null
                ? null
                : items.stream()
                        .map(item -> new SetmealService.ItemInput(item.dishId(), item.copies()))
                        .toList();
    }
}
