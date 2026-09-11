package com.sky.identity.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sky.common.domain.EnableStatus;
import com.sky.common.domain.PageQuery;
import com.sky.common.domain.PageResponse;
import com.sky.common.domain.SortSpec;
import com.sky.common.web.StatusPatchRequest;
import com.sky.identity.api.dto.EmployeeCreateRequest;
import com.sky.identity.api.dto.EmployeeResponse;
import com.sky.identity.api.dto.EmployeeUpdateRequest;
import com.sky.identity.domain.Employee;
import com.sky.identity.domain.EmployeeRole;
import com.sky.identity.service.EmployeeService;

/** 员工管理。整个前缀由 SecurityConfig 限制为 ADMIN。 */
@RestController
@RequestMapping("/api/v1/admin/employees")
public class EmployeeController {

    private final EmployeeService employeeService;

    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @GetMapping
    public PageResponse<EmployeeResponse> page(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) EmployeeRole role) {
        SortSpec sortSpec = SortSpec.parse(sort, EmployeeService.SORT_WHITELIST, "createdAt", true);
        PageResponse<Employee> result = employeeService.page(
                name, status, role, PageQuery.of(page, pageSize), sortSpec);
        return result.map(EmployeeResponse::from);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EmployeeResponse create(@Valid @RequestBody EmployeeCreateRequest request) {
        Employee created = employeeService.create(
                request.username(), request.password(), request.name(), request.phone(), request.role());
        return EmployeeResponse.from(created);
    }

    @GetMapping("/{id}")
    public EmployeeResponse getById(@PathVariable Long id) {
        return EmployeeResponse.from(employeeService.requireById(id));
    }

    @PutMapping("/{id}")
    public EmployeeResponse update(@PathVariable Long id, @Valid @RequestBody EmployeeUpdateRequest request) {
        Employee updated = employeeService.update(
                id, request.name(), request.phone(), request.role(), request.status());
        return EmployeeResponse.from(updated);
    }

    @PatchMapping("/{id}/status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changeStatus(@PathVariable Long id, @Valid @RequestBody StatusPatchRequest request) {
        employeeService.setStatus(id, EnableStatus.of(request.status()));
    }
}
