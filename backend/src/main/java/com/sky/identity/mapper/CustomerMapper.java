package com.sky.identity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import com.sky.identity.domain.Customer;

@Mapper
public interface CustomerMapper extends BaseMapper<Customer> {
}
