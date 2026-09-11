package com.sky.identity.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;

import com.sky.common.domain.EnableStatus;
import com.sky.common.domain.PageQuery;
import com.sky.common.domain.PageResponse;
import com.sky.common.domain.SortSpec;
import com.sky.common.error.BusinessException;
import com.sky.common.error.CommonErrorCode;
import com.sky.common.error.ErrorResponse;
import com.sky.common.util.Times;
import com.sky.identity.domain.Employee;
import com.sky.identity.domain.EmployeeRole;
import com.sky.identity.domain.IdentityErrorCode;
import com.sky.identity.mapper.EmployeeMapper;
import com.sky.security.Audience;
import com.sky.security.AuthErrorCode;
import com.sky.security.CurrentPrincipal;
import com.sky.security.TokenService;

/** 员工账号的读写与登录。 */
@Service
public class EmployeeService {

    /** 新建员工的初始密码。与种子数据里的 admin / zhangsan 保持一致,便于本地联调。 */
    private static final String INITIAL_PASSWORD = "123456";

    /** 与 docs/03-api.md §1.6 的白名单一致。 */
    private static final Set<String> SORT_WHITELIST = Set.of("createdAt", "username", "lastLoginAt");

    private final EmployeeMapper employeeMapper;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    public EmployeeService(EmployeeMapper employeeMapper, PasswordEncoder passwordEncoder, TokenService tokenService) {
        this.employeeMapper = employeeMapper;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
    }

    public record LoginResult(Employee employee, TokenService.IssuedTokens tokens) {
    }

    /**
     * 员工登录。
     *
     * <p>"用户名不存在"与"密码错误"返回**同一个**错误码:否则接口就变成了一个账号枚举器。
     */
    public LoginResult login(String username, String rawPassword) {
        Employee employee = employeeMapper.selectOne(
                Wrappers.<Employee>lambdaQuery().eq(Employee::getUsername, username));
        if (employee == null || !passwordEncoder.matches(rawPassword, employee.getPasswordHash())) {
            throw new BusinessException(AuthErrorCode.AUTH_BAD_CREDENTIALS);
        }
        if (!employee.isEnabled()) {
            throw new BusinessException(IdentityErrorCode.EMPLOYEE_DISABLED);
        }

        Employee touch = new Employee();
        touch.setId(employee.getId());
        touch.setLastLoginAt(Times.nowLocal());
        employeeMapper.updateById(touch);

        TokenService.IssuedTokens tokens = tokenService.issue(
                employee.getId(), Audience.ADMIN, employee.getRole().name());
        return new LoginResult(employee, tokens);
    }

    /** 按 id 取员工,不存在则 404。 */
    public Employee requireById(Long id) {
        Employee employee = employeeMapper.selectById(id);
        if (employee == null) {
            throw new BusinessException(IdentityErrorCode.EMPLOYEE_NOT_FOUND);
        }
        return employee;
    }

    public PageResponse<Employee> page(String name, PageQuery pageQuery, SortSpec sortSpec) {
        LambdaQueryWrapper<Employee> wrapper = Wrappers.<Employee>lambdaQuery()
                .like(StringUtils.hasText(name), Employee::getName, name);
        applySort(wrapper, sortSpec);
        Page<Employee> page = employeeMapper.selectPage(
                new Page<>(pageQuery.page(), pageQuery.pageSize()), wrapper);
        return PageResponse.from(page);
    }

    @Transactional
    public Employee create(String username, String name, String phone, EmployeeRole role) {
        if (employeeMapper.exists(Wrappers.<Employee>lambdaQuery().eq(Employee::getUsername, username))) {
            throw new BusinessException(IdentityErrorCode.EMPLOYEE_USERNAME_TAKEN);
        }
        Employee employee = new Employee();
        employee.setUsername(username);
        employee.setName(name);
        employee.setPhone(phone);
        employee.setRole(role);
        employee.setStatus(EnableStatus.ENABLED);
        employee.setPasswordHash(passwordEncoder.encode(INITIAL_PASSWORD));
        employeeMapper.insert(employee);
        return employee;
    }

    @Transactional
    public Employee update(Long id, String name, String phone, EmployeeRole role) {
        Employee existing = requireById(id);
        if (role != null && role != existing.getRole() && id.equals(currentEmployeeId())) {
            throw new BusinessException(IdentityErrorCode.EMPLOYEE_SELF_ROLE_CHANGE);
        }
        Employee update = new Employee();
        update.setId(id);
        update.setName(name);
        update.setPhone(phone);
        update.setRole(role);
        employeeMapper.updateById(update);
        return requireById(id);
    }

    /**
     * 启用 / 禁用员工。
     *
     * <p>禁用时同时撤销该员工的全部续期凭证——否则一个刚被停权的账号还能靠手里的 refresh token
     * 不断续期,停权形同虚设。
     */
    @Transactional
    public void setStatus(Long id, EnableStatus status) {
        requireById(id);
        if (!status.isEnabled() && id.equals(currentEmployeeId())) {
            throw new BusinessException(IdentityErrorCode.EMPLOYEE_SELF_DISABLE);
        }
        Employee update = new Employee();
        update.setId(id);
        update.setStatus(status);
        employeeMapper.updateById(update);

        if (!status.isEnabled()) {
            tokenService.revokeAll(Audience.ADMIN, id);
        }
    }

    /** 修改自己的密码,成功后撤销该员工全部续期凭证,强制各处重新登录。 */
    @Transactional
    public void changePassword(Long employeeId, String oldPassword, String newPassword) {
        Employee employee = requireById(employeeId);
        if (!passwordEncoder.matches(oldPassword, employee.getPasswordHash())) {
            throw new BusinessException(AuthErrorCode.AUTH_BAD_CREDENTIALS);
        }
        if (passwordEncoder.matches(newPassword, employee.getPasswordHash())) {
            throw new BusinessException(CommonErrorCode.COMMON_VALIDATION_FAILED, "新密码不能与原密码相同",
                    List.of(new ErrorResponse.Detail("newPassword", "不能与原密码相同")));
        }
        Employee update = new Employee();
        update.setId(employeeId);
        update.setPasswordHash(passwordEncoder.encode(newPassword));
        employeeMapper.updateById(update);

        tokenService.revokeAll(Audience.ADMIN, employeeId);
    }

    private static Long currentEmployeeId() {
        CurrentPrincipal principal = CurrentPrincipal.current();
        return principal == null ? null : principal.id();
    }

    private void applySort(LambdaQueryWrapper<Employee> wrapper, SortSpec sortSpec) {
        switch (sortSpec.field()) {
            case "createdAt" -> wrapper.orderBy(true, sortSpec.ascending(), Employee::getCreatedAt);
            case "username" -> wrapper.orderBy(true, sortSpec.ascending(), Employee::getUsername);
            case "lastLoginAt" -> wrapper.orderBy(true, sortSpec.ascending(), Employee::getLastLoginAt);
            default -> throw new BusinessException(CommonErrorCode.COMMON_SORT_FIELD_NOT_ALLOWED);
        }
        // 稳定分页:同值行必须有确定的次级顺序,否则翻页时会出现重复或丢行
        wrapper.orderByDesc(Employee::getId);
    }
}
