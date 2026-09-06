package com.sky.menu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sky.menu.dto.CategoryPageQueryDTO;
import com.sky.menu.entity.Category;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CategoryMapper extends BaseMapper<Category> {

    /**
     * 分页查询
     * @param page
     * @param categoryPageQueryDTO
     * @return
     */
    IPage<Category> pageQuery(Page<Category> page, @Param("dto") CategoryPageQueryDTO categoryPageQueryDTO);
}
