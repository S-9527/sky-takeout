package com.sky.identity.api;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sky.identity.api.dto.CustomerProfileUpdateRequest;
import com.sky.identity.api.dto.CustomerResponse;
import com.sky.identity.service.CustomerService;
import com.sky.security.CurrentPrincipal;

/** 顾客资料。永远只操作令牌主体自己的资料,路径里不接受 id——没有可被越权利用的输入。 */
@RestController
@RequestMapping("/api/v1/customer/profile")
public class CustomerProfileController {

    private final CustomerService customerService;

    public CustomerProfileController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping
    public CustomerResponse get() {
        return CustomerResponse.from(customerService.requireById(CurrentPrincipal.requireCustomerId()));
    }

    @PutMapping
    public CustomerResponse update(@Valid @RequestBody CustomerProfileUpdateRequest request) {
        return CustomerResponse.from(customerService.updateProfile(
                CurrentPrincipal.requireCustomerId(),
                request.nickname(),
                request.avatarUrl(),
                request.phone()));
    }
}
