package com.sky.profile.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import com.sky.common.error.BusinessException;
import com.sky.common.error.CommonErrorCode;
import com.sky.common.error.ErrorResponse;
import com.sky.profile.api.dto.UserAddressCreateRequest;
import com.sky.profile.api.dto.UserAddressResponse;
import com.sky.profile.api.dto.UserAddressUpdateRequest;
import com.sky.profile.service.AddressService;
import com.sky.security.CurrentPrincipal;

/**
 * 顾客地址簿。整个前缀由 SecurityConfig 限制为 CUSTOMER;
 * 顾客 id 一律取自令牌,路径里没有可越权的输入(R9)。
 */
@RestController
@RequestMapping("/api/v1/customer/addresses")
public class AddressController {

    private final AddressService addressService;

    public AddressController(AddressService addressService) {
        this.addressService = addressService;
    }

    @GetMapping
    public List<UserAddressResponse> list() {
        return addressService.list(CurrentPrincipal.requireCustomerId()).stream()
                .map(UserAddressResponse::from)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserAddressResponse create(@Valid @RequestBody UserAddressCreateRequest request) {
        return UserAddressResponse.from(addressService.create(
                CurrentPrincipal.requireCustomerId(),
                request.consignee(), request.phone(), request.province(), request.city(),
                request.district(), request.detail(), request.label(), flag(request.isDefault(), false)));
    }

    @GetMapping("/{id}")
    public UserAddressResponse getById(@PathVariable Long id) {
        return UserAddressResponse.from(
                addressService.requireOwned(CurrentPrincipal.requireCustomerId(), id));
    }

    @PutMapping("/{id}")
    public UserAddressResponse update(@PathVariable Long id, @Valid @RequestBody UserAddressUpdateRequest request) {
        return UserAddressResponse.from(addressService.update(
                CurrentPrincipal.requireCustomerId(), id,
                request.consignee(), request.phone(), request.province(), request.city(),
                request.district(), request.detail(), request.label(), flag(request.isDefault(), false)));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        addressService.delete(CurrentPrincipal.requireCustomerId(), id);
    }

    @PatchMapping("/{id}/default")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setDefault(@PathVariable Long id) {
        addressService.setDefault(CurrentPrincipal.requireCustomerId(), id);
    }

    /** 契约里 {@code isDefault} 是 1/0,非法取值必须落到统一错误体上。 */
    private static boolean flag(Integer value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value == 1) {
            return true;
        }
        if (value == 0) {
            return false;
        }
        throw new BusinessException(CommonErrorCode.COMMON_VALIDATION_FAILED,
                "isDefault 只能是 1 或 0",
                List.of(new ErrorResponse.Detail("isDefault", "只能是 1 或 0")));
    }
}
