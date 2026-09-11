package com.sky.catalog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import com.sky.catalog.domain.Category;

@Mapper
public interface CategoryMapper extends BaseMapper<Category> {
}
