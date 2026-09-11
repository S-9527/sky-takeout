package com.sky.catalog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.sky.catalog.domain.CatalogErrorCode;
import com.sky.catalog.domain.Category;
import com.sky.catalog.domain.CategoryType;
import com.sky.catalog.domain.Dish;
import com.sky.catalog.domain.DishFlavor;
import com.sky.catalog.mapper.DishFlavorMapper;
import com.sky.catalog.mapper.DishMapper;
import com.sky.common.domain.EnableStatus;
import com.sky.common.domain.PageQuery;
import com.sky.common.domain.PageResponse;
import com.sky.common.domain.SortSpec;
import com.sky.common.error.BusinessException;
import com.sky.common.error.CommonErrorCode;
import com.sky.common.error.ErrorResponse;

/** 菜品的读写,含口味配置的整体替换与"停售连带停售套餐"。 */
@Service
public class DishService {

    /** 与 openapi 的 pageDishes 描述一致。 */
    public static final Set<String> SORT_WHITELIST = Set.of("sortOrder", "priceCents", "createdAt", "name");

    private final DishMapper dishMapper;
    private final DishFlavorMapper dishFlavorMapper;
    private final CategoryService categoryService;

    public DishService(DishMapper dishMapper, DishFlavorMapper dishFlavorMapper, CategoryService categoryService) {
        this.dishMapper = dishMapper;
        this.dishFlavorMapper = dishFlavorMapper;
        this.categoryService = categoryService;
    }

    /**
     * 口味入参。刻意不用 api 层的 DTO:依赖方向只能是 api → service(L2),
     * 所以控制器负责把请求体映射成这个服务层类型。
     */
    public record FlavorInput(String name, List<String> options, Integer sortOrder) {
    }

    public PageResponse<Dish> page(String name, Long categoryId, Integer status,
                                   PageQuery pageQuery, SortSpec sortSpec) {
        LambdaQueryWrapper<Dish> wrapper = Wrappers.lambdaQuery();
        if (StringUtils.hasText(name)) {
            wrapper.like(Dish::getName, name);
        }
        if (categoryId != null) {
            wrapper.eq(Dish::getCategoryId, categoryId);
        }
        if (status != null) {
            wrapper.eq(Dish::getStatus, EnableStatus.of(status));
        }
        applySort(wrapper, sortSpec);

        Page<Dish> page = dishMapper.selectPage(new Page<>(pageQuery.page(), pageQuery.pageSize()), wrapper);
        return PageResponse.from(page);
    }

    public Dish requireById(Long id) {
        Dish dish = dishMapper.selectById(id);
        if (dish == null) {
            throw new BusinessException(CatalogErrorCode.DISH_NOT_FOUND);
        }
        return dish;
    }

    /** 某菜品的口味配置,按 sortOrder 升序。停售菜品也照常返回(配置不因停售丢失)。 */
    public List<DishFlavor> flavorsOf(Long dishId) {
        return dishFlavorMapper.selectList(Wrappers.<DishFlavor>lambdaQuery()
                .eq(DishFlavor::getDishId, dishId)
                .orderByAsc(DishFlavor::getSortOrder)
                .orderByAsc(DishFlavor::getId));
    }

    @Transactional
    public Dish create(Long categoryId, String name, Long priceCents, String imageUrl, String description,
                       Integer status, Integer sortOrder, List<FlavorInput> flavors) {
        // 新增必须落在启用中的菜品分类下
        requireDishCategory(categoryId, true);
        requireNameAvailable(categoryId, name, null);

        Dish dish = new Dish();
        dish.setCategoryId(categoryId);
        dish.setName(name);
        dish.setPriceCents(priceCents);
        dish.setImageUrl(imageUrl);
        dish.setDescription(description);
        // 不传就停售:数据库默认值是 0,上架必须是显式操作,避免"建完直接卖"这种误上架
        dish.setStatus(status == null ? EnableStatus.DISABLED : EnableStatus.of(status));
        dish.setSortOrder(sortOrder == null ? 0 : sortOrder);
        dishMapper.insert(dish);

        replaceFlavors(dish.getId(), flavors);
        return requireById(dish.getId());
    }

    /**
     * 编辑菜品。
     *
     * @param flavors {@code null} 表示不改口味配置;传了(含空数组)则**整体替换**(领域文档 §3.5)
     */
    @Transactional
    public Dish update(Long id, Long categoryId, String name, Long priceCents, String imageUrl,
                       String description, Integer status, Integer sortOrder, List<FlavorInput> flavors) {
        Dish existing = requireById(id);
        boolean categoryChanged = !categoryId.equals(existing.getCategoryId());
        // 只有真的换分类时才要求目标分类处于启用状态:否则一个分类被禁用后,
        // 连改自己菜品的名字都会被挡住
        requireDishCategory(categoryId, categoryChanged);
        requireNameAvailable(categoryId, name, id);

        Dish update = new Dish();
        update.setId(id);
        update.setCategoryId(categoryId);
        update.setName(name);
        update.setPriceCents(priceCents);
        update.setImageUrl(imageUrl);
        update.setDescription(description);
        update.setStatus(EnableStatus.of(status));
        update.setSortOrder(sortOrder);
        dishMapper.updateById(update);

        if (flavors != null) {
            replaceFlavors(id, flavors);
        }
        if (!update.getStatus().isEnabled()) {
            dishMapper.disableSetmealsContaining(List.of(id));
        }
        return requireById(id);
    }

    /**
     * 批量真删除(D13)。口味配置由外键级联删除(领域文档 §3.5)。
     *
     * <p>被套餐引用的菜品删不掉,由 {@code setmeal_item} 的外键 {@code ON DELETE RESTRICT} 强制,
     * 这里把约束违例翻译成 422。
     */
    @Transactional
    public void delete(List<Long> ids) {
        requireAllExist(ids);
        try {
            dishMapper.deleteByIds(ids);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(CatalogErrorCode.SETMEAL_CONTAINS_DISH);
        }
    }

    /**
     * 批量起售 / 停售。
     *
     * <p>停售会连带停售包含这些菜品的套餐(同一事务);起售不会自动起售套餐——
     * 套餐要不要卖是独立决定,起售套餐时另有"所含菜品必须全部起售"的校验。
     */
    @Transactional
    public void changeStatus(List<Long> ids, EnableStatus status) {
        requireAllExist(ids);

        Dish update = new Dish();
        update.setStatus(status);
        dishMapper.update(update, Wrappers.<Dish>lambdaUpdate().in(Dish::getId, ids));

        if (!status.isEnabled()) {
            dishMapper.disableSetmealsContaining(ids);
        }
    }

    private void requireAllExist(List<Long> ids) {
        List<Dish> found = dishMapper.selectBatchIds(ids);
        if (found.size() != ids.size()) {
            throw new BusinessException(CatalogErrorCode.DISH_NOT_FOUND);
        }
    }

    private Category requireDishCategory(Long categoryId, boolean mustBeEnabled) {
        Category category = categoryService.requireById(categoryId);
        if (category.getType() != CategoryType.DISH) {
            throw new BusinessException(CatalogErrorCode.DISH_CATEGORY_TYPE_MISMATCH);
        }
        if (mustBeEnabled && (category.getStatus() == null || !category.getStatus().isEnabled())) {
            throw new BusinessException(CatalogErrorCode.CATEGORY_DISABLED);
        }
        return category;
    }

    private void requireNameAvailable(Long categoryId, String name, Long excludeId) {
        LambdaQueryWrapper<Dish> wrapper = Wrappers.<Dish>lambdaQuery()
                .eq(Dish::getCategoryId, categoryId)
                .eq(Dish::getName, name);
        if (excludeId != null) {
            wrapper.ne(Dish::getId, excludeId);
        }
        if (dishMapper.exists(wrapper)) {
            throw new BusinessException(CatalogErrorCode.DISH_NAME_TAKEN);
        }
    }

    /** 整体替换:先清空该菜品的口味,再按请求顺序写入;同一菜品内口味维度名不能重复。 */
    private void replaceFlavors(Long dishId, List<FlavorInput> flavors) {
        dishFlavorMapper.delete(Wrappers.<DishFlavor>lambdaQuery().eq(DishFlavor::getDishId, dishId));
        if (flavors == null || flavors.isEmpty()) {
            return;
        }
        Set<String> seen = new HashSet<>();
        int index = 0;
        for (FlavorInput input : flavors) {
            if (input.name() == null || input.name().isBlank()) {
                throw new BusinessException(CommonErrorCode.COMMON_VALIDATION_FAILED,
                        "口味维度名不能为空",
                        List.of(new ErrorResponse.Detail("flavors[" + index + "].name", "不能为空")));
            }
            if (input.options() == null || input.options().isEmpty()) {
                throw new BusinessException(CommonErrorCode.COMMON_VALIDATION_FAILED,
                        "口味选项至少一个",
                        List.of(new ErrorResponse.Detail("flavors[" + index + "].options", "至少一个选项")));
            }
            if (!seen.add(input.name())) {
                // 数据库有 (dish_id, name) 唯一键兜底,但那会变成 409/500;这里给一个明确的 400
                throw new BusinessException(CommonErrorCode.COMMON_VALIDATION_FAILED,
                        "同一菜品的口味维度名不能重复",
                        List.of(new ErrorResponse.Detail("flavors[" + index + "].name", "与前面的口味维度重名")));
            }
            DishFlavor flavor = new DishFlavor();
            flavor.setDishId(dishId);
            flavor.setName(input.name());
            flavor.setOptions(input.options());
            flavor.setSortOrder(input.sortOrder() == null ? index : input.sortOrder());
            dishFlavorMapper.insert(flavor);
            index++;
        }
    }

    private void applySort(LambdaQueryWrapper<Dish> wrapper, SortSpec sortSpec) {
        switch (sortSpec.field()) {
            case "sortOrder" -> wrapper.orderBy(true, sortSpec.ascending(), Dish::getSortOrder);
            case "priceCents" -> wrapper.orderBy(true, sortSpec.ascending(), Dish::getPriceCents);
            case "createdAt" -> wrapper.orderBy(true, sortSpec.ascending(), Dish::getCreatedAt);
            case "name" -> wrapper.orderBy(true, sortSpec.ascending(), Dish::getName);
            default -> throw new BusinessException(CommonErrorCode.COMMON_SORT_FIELD_NOT_ALLOWED);
        }
        wrapper.orderByAsc(Dish::getId);
    }
}
