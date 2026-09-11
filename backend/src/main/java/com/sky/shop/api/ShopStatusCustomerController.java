package com.sky.shop.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sky.shop.api.dto.ShopStatusViewResponse;
import com.sky.shop.service.ShopStatusService;

/**
 * 顾客端营业状态与公告。整个前缀由 SecurityConfig 限制为 CUSTOMER。
 *
 * <p>打烊时这个接口照常 200(R1:顾客可以浏览、只是不能下单)。
 */
@RestController
@RequestMapping("/api/v1/customer/shop/status")
public class ShopStatusCustomerController {

    private final ShopStatusService shopStatusService;

    public ShopStatusCustomerController(ShopStatusService shopStatusService) {
        this.shopStatusService = shopStatusService;
    }

    @GetMapping
    public ShopStatusViewResponse get() {
        return ShopStatusViewResponse.from(shopStatusService.requireCurrent());
    }
}
