package com.sky.identity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import com.sky.identity.domain.Employee;

@Mapper
public interface EmployeeMapper extends BaseMapper<Employee> {
}
