package com.sky.employee.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sky.employee.dto.EmployeePageQueryDTO;
import com.sky.employee.entity.Employee;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface EmployeeMapper extends BaseMapper<Employee> {

    /**
     * 分页查询
     * @param page
     * @param employeePageQueryDTO
     * @return
     */
    IPage<Employee> pageQuery(Page<Employee> page, @Param("dto") EmployeePageQueryDTO employeePageQueryDTO);
}
