package com.sky.catalog.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import com.sky.catalog.api.dto.CategoryResponse;
import com.sky.catalog.domain.CategoryType;
import com.sky.catalog.service.CategoryService;

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

    public CustomerCatalogController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    /** 顾客端点菜/点套餐的分类列表:只返回启用中的分类,按 sortOrder 升序。 */
    @GetMapping("/categories")
    public List<CategoryResponse> categories(@RequestParam CategoryType type) {
        return categoryService.list(type, false).stream().map(CategoryResponse::from).toList();
    }
}
