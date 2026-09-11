package com.sky.catalog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;

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

/** 商品分类的读写。 */
@Service
public class CategoryService {

    /** 与 docs/openapi.yaml 的 pageCategories 描述一致。 */
    public static final Set<String> SORT_WHITELIST = Set.of("sortOrder", "name", "createdAt");

    private final CategoryMapper categoryMapper;

    public CategoryService(CategoryMapper categoryMapper) {
        this.categoryMapper = categoryMapper;
    }

    public PageResponse<Category> page(String name, CategoryType type, Integer status,
                                       PageQuery pageQuery, SortSpec sortSpec) {
        LambdaQueryWrapper<Category> wrapper = Wrappers.lambdaQuery();
        if (StringUtils.hasText(name)) {
            wrapper.like(Category::getName, name);
        }
        if (type != null) {
            wrapper.eq(Category::getType, type);
        }
        if (status != null) {
            // 非法取值(2 之类)在这里就报 400,不静默当成"禁用"
            wrapper.eq(Category::getStatus, EnableStatus.of(status));
        }
        applySort(wrapper, sortSpec);

        Page<Category> page = categoryMapper.selectPage(new Page<>(pageQuery.page(), pageQuery.pageSize()), wrapper);
        return PageResponse.from(page);
    }

    /**
     * 下拉用的分类列表:不分页,按 {@code sortOrder} 升序。
     *
     * @param includeDisabled true 时连禁用的一起返回(管理端编辑旧商品需要),顾客端恒为 false
     */
    public List<Category> list(CategoryType type, boolean includeDisabled) {
        LambdaQueryWrapper<Category> wrapper = Wrappers.<Category>lambdaQuery()
                .eq(Category::getType, type);
        if (!includeDisabled) {
            wrapper.eq(Category::getStatus, EnableStatus.ENABLED);
        }
        // 同 sortOrder 时按 id 升序:列表顺序必须确定,否则前端下拉会随机跳动
        return categoryMapper.selectList(wrapper.orderByAsc(Category::getSortOrder).orderByAsc(Category::getId));
    }

    public Category requireById(Long id) {
        Category category = categoryMapper.selectById(id);
        if (category == null) {
            throw new BusinessException(CatalogErrorCode.CATEGORY_NOT_FOUND);
        }
        return category;
    }

    @Transactional
    public Category create(String name, CategoryType type, Integer sortOrder, Integer status) {
        requireNameAvailable(type, name, null);
        Category category = new Category();
        category.setName(name);
        category.setType(type);
        category.setSortOrder(sortOrder == null ? 0 : sortOrder);
        // 不传就是启用:新建分类的默认用途就是"马上能往里加商品"
        category.setStatus(status == null ? EnableStatus.ENABLED : EnableStatus.of(status));
        categoryMapper.insert(category);
        // createdAt/updatedAt 由数据库默认值产生,insert 后必须回读才能返回给前端
        return requireById(category.getId());
    }

    /**
     * 编辑分类。类型不可改:已经引用它的菜品/套餐会落到错误类型的分类下。
     *
     * <p>请求体里的 {@code type} 是可选的:不传表示"我不改类型";传了就必须与当前一致,
     * 否则 422 {@code CATEGORY_TYPE_IMMUTABLE}。这样"试图改类型"是一个明确的错误,
     * 而不是被静默忽略——静默忽略会让前端以为改成功了。
     */
    @Transactional
    public Category update(Long id, String name, CategoryType type, Integer sortOrder, Integer status) {
        Category existing = requireById(id);
        if (type != null && type != existing.getType()) {
            throw new BusinessException(CatalogErrorCode.CATEGORY_TYPE_IMMUTABLE);
        }
        requireNameAvailable(existing.getType(), name, id);

        Category update = new Category();
        update.setId(id);
        update.setName(name);
        update.setSortOrder(sortOrder);
        update.setStatus(EnableStatus.of(status));
        categoryMapper.updateById(update);
        return requireById(id);
    }

    /**
     * 真删除(D13:不做软删除)。
     *
     * <p>被商品引用的分类删不掉,由数据库外键 {@code ON DELETE RESTRICT} 强制;
     * 这里把约束违例翻译成 422,而不是让前端收到 500。用"先查引用再删"会有 TOCTOU,
     * 直接依赖约束反而更准。
     */
    @Transactional
    public void delete(Long id) {
        requireById(id);
        try {
            categoryMapper.deleteById(id);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(CatalogErrorCode.CATEGORY_IN_USE);
        }
    }

    @Transactional
    public void setStatus(Long id, EnableStatus status) {
        requireById(id);
        Category update = new Category();
        update.setId(id);
        update.setStatus(status);
        categoryMapper.updateById(update);
    }

    private void requireNameAvailable(CategoryType type, String name, Long excludeId) {
        LambdaQueryWrapper<Category> wrapper = Wrappers.<Category>lambdaQuery()
                .eq(Category::getType, type)
                .eq(Category::getName, name);
        if (excludeId != null) {
            wrapper.ne(Category::getId, excludeId);
        }
        if (categoryMapper.exists(wrapper)) {
            throw new BusinessException(CatalogErrorCode.CATEGORY_NAME_TAKEN);
        }
    }

    private void applySort(LambdaQueryWrapper<Category> wrapper, SortSpec sortSpec) {
        switch (sortSpec.field()) {
            case "sortOrder" -> wrapper.orderBy(true, sortSpec.ascending(), Category::getSortOrder);
            case "name" -> wrapper.orderBy(true, sortSpec.ascending(), Category::getName);
            case "createdAt" -> wrapper.orderBy(true, sortSpec.ascending(), Category::getCreatedAt);
            default -> throw new BusinessException(CommonErrorCode.COMMON_SORT_FIELD_NOT_ALLOWED);
        }
        // 稳定分页:同值行必须有确定的次级顺序,否则翻页时会重复或丢行
        wrapper.orderByAsc(Category::getId);
    }
}
