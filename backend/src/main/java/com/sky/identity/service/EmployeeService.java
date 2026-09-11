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

    /** 与 docs/03-api.md §1.6 的白名单一致。控制器解析 sort 参数时复用它,避免两处各写一份。 */
    public static final Set<String> SORT_WHITELIST = Set.of("createdAt", "username", "lastLoginAt");

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

    public PageResponse<Employee> page(String name, Integer status, EmployeeRole role,
                                       PageQuery pageQuery, SortSpec sortSpec) {
        LambdaQueryWrapper<Employee> wrapper = Wrappers.lambdaQuery();
        if (StringUtils.hasText(name)) {
            // 契约规定 name 同时匹配用户名与姓名:管理员记的是人名,不该逼他先判断该填哪个
            wrapper.and(w -> w.like(Employee::getUsername, name).or().like(Employee::getName, name));
        }
        if (status != null) {
            wrapper.eq(Employee::getStatus, EnableStatus.of(status));
        }
        if (role != null) {
            wrapper.eq(Employee::getRole, role);
        }
        applySort(wrapper, sortSpec);
        Page<Employee> page = employeeMapper.selectPage(
                new Page<>(pageQuery.page(), pageQuery.pageSize()), wrapper);
        return PageResponse.from(page);
    }

    @Transactional
    public Employee create(String username, String rawPassword, String name, String phone, EmployeeRole role) {
        if (employeeMapper.exists(Wrappers.<Employee>lambdaQuery().eq(Employee::getUsername, username))) {
            throw new BusinessException(IdentityErrorCode.EMPLOYEE_USERNAME_TAKEN);
        }
        Employee employee = new Employee();
        employee.setUsername(username);
        employee.setName(name);
        employee.setPhone(phone);
        employee.setRole(role);
        employee.setStatus(EnableStatus.ENABLED);
        employee.setPasswordHash(passwordEncoder.encode(rawPassword));
        employeeMapper.insert(employee);
        return employee;
    }

    /**
     * 编辑员工。可改姓名、手机号、角色、状态;用户名不可改。
     *
     * <p>两个自我保护:超管不能把自己降级,也不能把自己禁用——否则一次误操作就能把系统锁死,
     * 只能去数据库里改回来。
     */
    @Transactional
    public Employee update(Long id, String name, String phone, EmployeeRole role, Integer status) {
        Employee existing = requireById(id);
        EnableStatus targetStatus = EnableStatus.of(status);
        boolean self = id.equals(currentEmployeeId());
        if (self && role != existing.getRole()) {
            throw new BusinessException(IdentityErrorCode.EMPLOYEE_SELF_ROLE_CHANGE);
        }
        if (self && !targetStatus.isEnabled()) {
            throw new BusinessException(IdentityErrorCode.EMPLOYEE_SELF_DISABLE);
        }

        Employee update = new Employee();
        update.setId(id);
        update.setName(name);
        update.setPhone(phone);
        update.setRole(role);
        update.setStatus(targetStatus);
        employeeMapper.updateById(update);

        if (!targetStatus.isEnabled()) {
            tokenService.revokeAll(Audience.ADMIN, id);
        }
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
