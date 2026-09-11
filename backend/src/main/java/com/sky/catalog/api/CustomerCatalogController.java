package com.sky.catalog.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import com.sky.catalog.api.dto.CategoryResponse;
import com.sky.catalog.api.dto.DishDetailResponse;
import com.sky.catalog.api.dto.DishFlavorResponse;
import com.sky.catalog.api.dto.DishResponse;
import com.sky.catalog.api.dto.SetmealDetailResponse;
import com.sky.catalog.api.dto.SetmealItemResponse;
import com.sky.catalog.api.dto.SetmealResponse;
import com.sky.catalog.domain.CategoryType;
import com.sky.catalog.domain.Dish;
import com.sky.catalog.domain.Setmeal;
import com.sky.catalog.service.CategoryService;
import com.sky.catalog.service.DishService;
import com.sky.catalog.service.SetmealService;
import com.sky.common.domain.PageQuery;
import com.sky.common.domain.PageResponse;

/**
 * 顾客端商品目录。整个前缀由 SecurityConfig 限制为 CUSTOMER。
 *
 * <p>顾客端只看得见**启用中**的分类与**起售**的商品:可见性由服务端决定,
 * 不依赖前端过滤(前端过滤挡不住直接调接口的人)。
 */
@RestController
@RequestMapping("/api/v1/customer/catalog")
public class CustomerCatalogController {

    private final CategoryService categoryService;
    private final DishService dishService;
    private final SetmealService setmealService;

    public CustomerCatalogController(CategoryService categoryService, DishService dishService,
                                     SetmealService setmealService) {
        this.categoryService = categoryService;
        this.dishService = dishService;
        this.setmealService = setmealService;
    }

    /** 顾客端点菜/点套餐的分类列表:只返回启用中的分类,按 sortOrder 升序。 */
    @GetMapping("/categories")
    public List<CategoryResponse> categories(@RequestParam CategoryType type) {
        return categoryService.list(type, false).stream().map(CategoryResponse::from).toList();
    }

    /** 按分类查起售菜品。分类不存在 → 404;分类不可见 → 空页。 */
    @GetMapping("/dishes")
    public PageResponse<DishResponse> dishes(
            @RequestParam Long categoryId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        PageResponse<Dish> result = dishService.pageCustomerVisible(categoryId, PageQuery.of(page, pageSize));
        Map<Long, String> categoryNames = categoryService.namesByIds(
                result.records().stream().map(Dish::getCategoryId).distinct().toList());
        return result.map(dish -> DishResponse.from(dish, categoryNames.get(dish.getCategoryId())));
    }

    /** 菜品详情(含口味选项)。停售或分类不可见 → 422 DISH_OFF_SALE。 */
    @GetMapping("/dishes/{id}")
    public DishDetailResponse dish(@PathVariable Long id) {
        Dish dish = dishService.requireCustomerVisible(id);
        return DishDetailResponse.from(dish, categoryService.requireById(dish.getCategoryId()).getName(),
                dishService.flavorsOf(dish.getId()).stream().map(DishFlavorResponse::from).toList());
    }

    /** 起售套餐列表。categoryId 可选:不传表示全部。 */
    @GetMapping("/setmeals")
    public PageResponse<SetmealResponse> setmeals(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        PageResponse<Setmeal> result = setmealService.pageCustomerVisible(categoryId, PageQuery.of(page, pageSize));
        Map<Long, String> categoryNames = categoryService.namesByIds(
                result.records().stream().map(Setmeal::getCategoryId).distinct().toList());
        return result.map(setmeal -> SetmealResponse.from(setmeal, categoryNames.get(setmeal.getCategoryId())));
    }

    /** 套餐详情(含所含菜品)。停售或分类不可见 → 422 SETMEAL_OFF_SALE。 */
    @GetMapping("/setmeals/{id}")
    public SetmealDetailResponse setmeal(@PathVariable Long id) {
        Setmeal setmeal = setmealService.requireCustomerVisible(id);
        return SetmealDetailResponse.from(setmeal,
                categoryService.requireById(setmeal.getCategoryId()).getName(),
                setmealService.itemsOf(setmeal.getId()).stream().map(SetmealItemResponse::from).toList());
    }
}
