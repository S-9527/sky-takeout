package com.sky.shop.api;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sky.shop.api.dto.ShopStatusResponse;
import com.sky.shop.api.dto.ShopStatusUpdateRequest;
import com.sky.shop.service.ShopStatusService;

/** 管理端营业状态:查询与切换。整个前缀由 SecurityConfig 限制为 ADMIN / STAFF。 */
@RestController
@RequestMapping("/api/v1/admin/shop/status")
public class ShopStatusAdminController {

    private final ShopStatusService shopStatusService;

    public ShopStatusAdminController(ShopStatusService shopStatusService) {
        this.shopStatusService = shopStatusService;
    }

    @GetMapping
    public ShopStatusResponse get() {
        return ShopStatusResponse.from(shopStatusService.requireCurrent());
    }

    @PutMapping
    public ShopStatusResponse update(@Valid @RequestBody ShopStatusUpdateRequest request) {
        return ShopStatusResponse.from(shopStatusService.update(
                request.isOpen(), request.openTime(), request.closeTime(), request.notice()));
    }
}
