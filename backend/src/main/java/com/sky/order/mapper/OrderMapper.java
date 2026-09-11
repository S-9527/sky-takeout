package com.sky.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import com.sky.order.domain.Order;

@Mapper
public interface OrderMapper extends BaseMapper<Order> {
}
