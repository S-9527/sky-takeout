package com.sky.employee.controller;

import com.sky.employee.dto.EmployeeDTO;
import com.sky.employee.dto.EmployeeLoginDTO;
import com.sky.employee.dto.EmployeePageQueryDTO;
import com.sky.employee.dto.PasswordEditDTO;
import com.sky.employee.entity.Employee;
import com.sky.dto.RefreshTokenDTO;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.employee.service.EmployeeService;
import com.sky.employee.vo.EmployeeLoginVO;
import com.sky.auth.token.TokenPair;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;

/**
 * 员工管理
 */
@RestController
@RequestMapping("/admin/employee")
@Slf4j
@Tag(name = "员工相关接口")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;

    /**
     * 登录
     *
     * @param employeeLoginDTO
     * @return
     */
    @PostMapping("/login")
    @Operation(summary = "员工登录")
    public Result<EmployeeLoginVO> login(@RequestBody EmployeeLoginDTO employeeLoginDTO) {
        log.info("员工登录：{}", employeeLoginDTO.getUsername());

        return Result.success(employeeService.login(employeeLoginDTO));
    }

    /**
     * 退出
     *
     * @return
     */
    @PostMapping("/logout")
    @Operation(summary = "员工退出")
    public Result<String> logout(@RequestHeader(name = "Authorization", required = false) String authorization,
                                 @RequestBody(required = false) RefreshTokenDTO refreshTokenDTO) {
        String accessToken = null;
        if (authorization != null && authorization.startsWith("Bearer ")) {
            accessToken = authorization.substring("Bearer ".length());
        }
        employeeService.logout(accessToken,
                refreshTokenDTO != null ? refreshTokenDTO.getRefreshToken() : null);
        return Result.success();
    }

    /**
     * 刷新令牌对
     *
     * @return
     */
    @PostMapping("/refresh")
    @Operation(summary = "员工令牌刷新")
    public Result<TokenPair> refresh(@RequestBody RefreshTokenDTO refreshTokenDTO) {
        log.info("员工令牌刷新");
        return Result.success(employeeService.refresh(refreshTokenDTO.getRefreshToken()));
    }

    /**
     * 新增员工
     * @param employeeDTO
     * @return
     */
    @PostMapping
    @Operation(summary = "新增员工")
    public Result<String> save(@RequestBody EmployeeDTO employeeDTO){
        employeeService.save(employeeDTO);
        return Result.success();
    }

    /**
     * 员工分页查询
     * @param employeePageQueryDTO
     * @return
     */
    @GetMapping("/page")
    @Operation(summary = "员工分页查询")
    public Result<PageResult<Employee>> page(EmployeePageQueryDTO employeePageQueryDTO){
        PageResult<Employee> pageResult = employeeService.pageQuery(employeePageQueryDTO);
        return Result.success(pageResult);
    }

    /**
     * 启用禁用员工账号
     * @param status
     * @param id
     * @return
     */
    @PostMapping("/status/{status}")
    @Operation(summary = "启用禁用员工账号")
    public Result<String> startOrStop(@PathVariable Integer status,Long id){
        employeeService.startOrStop(status,id);
        return Result.success();
    }

    /**
     * 根据id查询员工信息
     * @param id
     * @return
     */
    @GetMapping("/{id}")
    @Operation(summary = "根据id查询员工信息")
    public Result<Employee> getById(@PathVariable Long id){
        Employee employee = employeeService.getById(id);
        return Result.success(employee);
    }

    /**
     * 编辑员工信息
     * @param employeeDTO
     * @return
     */
    @PutMapping
    @Operation(summary = "编辑员工信息")
    public Result<String> update(@RequestBody EmployeeDTO employeeDTO){
        employeeService.update(employeeDTO);
        return Result.success();
    }

    /**
     * 修改密码
     * @param passwordEditDTO
     * @return
     */
    @PutMapping("/editPassword")
    @Operation(summary = "修改密码")
    public Result<String> editPassword(@RequestBody PasswordEditDTO passwordEditDTO){
        employeeService.editPassword(passwordEditDTO);
        return Result.success();
    }
}
