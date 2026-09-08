package com.sky.employee.service;

import com.sky.employee.dto.EmployeeDTO;
import com.sky.employee.dto.EmployeeLoginDTO;
import com.sky.employee.dto.EmployeePageQueryDTO;
import com.sky.employee.dto.PasswordEditDTO;
import com.sky.employee.entity.Employee;
import com.sky.employee.vo.EmployeeLoginVO;
import com.sky.result.PageResult;

public interface EmployeeService {

    /**
     * 员工登录
     * @param employeeLoginDTO
     * @return
     */
    EmployeeLoginVO login(EmployeeLoginDTO employeeLoginDTO);

    /**
     * 新增员工
     * @param employeeDTO
     */
    void save(EmployeeDTO employeeDTO);

    /**
     * 分页查询
     * @param employeePageQueryDTO
     * @return
     */
    PageResult<Employee> pageQuery(EmployeePageQueryDTO employeePageQueryDTO);

    /**
     * 启用禁用员工账号
     * @param status
     * @param id
     */
    void startOrStop(Integer status, Long id);

    /**
     * 根据id查询员工
     * @param id
     * @return
     */
    Employee getById(Long id);

    /**
     * 编辑员工信息
     * @param employeeDTO
     */
    void update(EmployeeDTO employeeDTO);

    /**
     * 修改密码
     * @param passwordEditDTO
     */
    void editPassword(PasswordEditDTO passwordEditDTO);

    /**
     * 退出登录：撤销当前令牌，使其立即失效
     * @param token 待撤销的 jwt 令牌
     */
    void logout(String token);
}
