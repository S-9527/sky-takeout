package com.sky.employee.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.employee.enumeration.AccountStatus;
import com.sky.employee.dto.EmployeeDTO;
import com.sky.employee.dto.EmployeeLoginDTO;
import com.sky.employee.dto.EmployeePageQueryDTO;
import com.sky.employee.dto.PasswordEditDTO;
import com.sky.employee.entity.Employee;
import com.sky.exception.BaseException;
import com.sky.result.ResultCode;
import com.sky.employee.mapper.EmployeeMapper;
import com.sky.result.PageResult;
import com.sky.employee.service.EmployeeService;
import com.sky.employee.vo.EmployeeLoginVO;
import com.sky.token.JwtTokenService;
import com.sky.token.TokenPair;
import com.sky.token.TokenType;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmployeeServiceImpl implements EmployeeService {

    private final EmployeeMapper employeeMapper;

    private final PasswordEncoder passwordEncoder;

    private final JwtTokenService jwtTokenService;

    /**
     * 新员工初始密码，可用配置项 sky.employee.default-password 覆盖
     */
    @Value("${sky.employee.default-password:123456}")
    private String defaultPassword;

    /**
     * 员工登录
     *
     * @param employeeLoginDTO
     * @return
     */
    public EmployeeLoginVO login(EmployeeLoginDTO employeeLoginDTO) {
        String username = employeeLoginDTO.getUsername();
        String password = employeeLoginDTO.getPassword();

        //1、根据用户名查询数据库中的数据
        Employee employee = employeeMapper.selectOne(new LambdaQueryWrapper<Employee>()
                .eq(Employee::getUsername, username));

        //2、处理各种异常情况（用户名不存在或密码不对统一提示，防止账号枚举；账号锁定单独提示）
        if (employee == null) {
            //账号不存在
            throw new BadCredentialsException(MessageConstant.LOGIN_CREDENTIAL_ERROR);
        }

        //密码比对（BCrypt 校验，兼容 $2a/$2b/$2y 前缀）
        if (!passwordEncoder.matches(password, employee.getPassword())) {
            //密码错误
            throw new BadCredentialsException(MessageConstant.LOGIN_CREDENTIAL_ERROR);
        }

        if (AccountStatus.DISABLED.getCode().equals(employee.getStatus())) {
            //账号被锁定
            throw new LockedException(MessageConstant.ACCOUNT_LOCKED);
        }

        //3、登录成功，签发JWT令牌并组装登录结果
        TokenPair tokenPair = jwtTokenService.createTokenPair(TokenType.ADMIN, employee.getId());
        return EmployeeLoginVO.builder()
                .id(employee.getId())
                .userName(employee.getUsername())
                .name(employee.getName())
                .accessToken(tokenPair.getAccessToken())
                .refreshToken(tokenPair.getRefreshToken())
                .build();
    }

    /**
     * 刷新令牌对：校验并轮换刷新令牌，返回新的访问+刷新令牌
     *
     * @param refreshToken 刷新令牌
     * @return 新的令牌对
     */
    public TokenPair refresh(String refreshToken) {
        return jwtTokenService.refresh(TokenType.ADMIN, refreshToken);
    }

    /**
     * 新增员工
     *
     * @param employeeDTO
     */
    public void save(EmployeeDTO employeeDTO) {
        Employee employee = new Employee();

        //对象属性拷贝
        BeanUtils.copyProperties(employeeDTO, employee);

        //设置账号的状态，默认正常状态 1表示正常 0表示锁定
        employee.setStatus(AccountStatus.ENABLED.getCode());

        //设置密码，默认密码123456
        employee.setPassword(passwordEncoder.encode(defaultPassword));

        employeeMapper.insert(employee);
    }

    /**
     * 分页查询
     *
     * @param employeePageQueryDTO
     * @return
     */
    public PageResult<Employee> pageQuery(EmployeePageQueryDTO employeePageQueryDTO) {
        IPage<Employee> page = employeeMapper.pageQuery(
                new Page<>(employeePageQueryDTO.getPage(), employeePageQueryDTO.getPageSize()),
                employeePageQueryDTO);

        long total = page.getTotal();
        List<Employee> records = page.getRecords();

        return new PageResult<>(total, records);
    }

    /**
     * 启用禁用员工账号
     *
     * @param status
     * @param id
     */
    public void startOrStop(Integer status, Long id) {
        AccountStatus accountStatus = AccountStatus.fromCode(status);
        if (accountStatus == null) {
            throw new BaseException(ResultCode.UNPROCESSABLE_ENTITY.getCode(), "非法的账号状态值：" + status);
        }

        Employee employee = Employee.builder()
                .status(accountStatus.getCode())
                .id(id)
                .build();

        employeeMapper.updateById(employee);

        log.info("设置{}账号状态为：{}", id, accountStatus.getDesc());
    }

    /**
     * 根据id查询员工
     *
     * @param id
     * @return
     */
    public Employee getById(Long id) {
        Employee employee = employeeMapper.selectById(id);
        employee.setPassword("****");
        return employee;
    }

    /**
     * 编辑员工信息
     *
     * @param employeeDTO
     */
    public void update(EmployeeDTO employeeDTO) {
        Employee employee = new Employee();
        BeanUtils.copyProperties(employeeDTO, employee);

        employeeMapper.updateById(employee);
    }

    /**
     * 修改密码
     *
     * @param passwordEditDTO
     */
    public void editPassword(PasswordEditDTO passwordEditDTO) {
        //当前登录员工
        Long empId = BaseContext.getCurrentId();
        Employee employee = employeeMapper.selectById(empId);
        if (employee == null) {
            throw new UsernameNotFoundException(MessageConstant.ACCOUNT_NOT_FOUND);
        }

        //校验旧密码
        if (!passwordEncoder.matches(passwordEditDTO.getOldPassword(), employee.getPassword())) {
            throw new BadCredentialsException(MessageConstant.PASSWORD_ERROR);
        }

        //更新为新密码
        employee.setPassword(passwordEncoder.encode(passwordEditDTO.getNewPassword()));
        employeeMapper.updateById(employee);
    }

    /**
     * 退出登录：同时撤销访问令牌与刷新令牌
     *
     * @param accessToken  访问令牌
     * @param refreshToken 刷新令牌(可为空)
     */
    public void logout(String accessToken, String refreshToken) {
        if (accessToken != null && !accessToken.isEmpty()) {
            jwtTokenService.revokeToken(accessToken, TokenType.ADMIN);
        }
        if (refreshToken != null && !refreshToken.isEmpty()) {
            jwtTokenService.revokeToken(refreshToken, TokenType.ADMIN);
        }
    }
}
