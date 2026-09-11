package com.sky.cart.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import com.sky.cart.domain.CartItem;

@Mapper
public interface CartItemMapper extends BaseMapper<CartItem> {
}
