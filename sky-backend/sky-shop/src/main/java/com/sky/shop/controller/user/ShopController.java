package com.sky.shop.controller.user;

import com.sky.result.Result;
import com.sky.shop.vo.MerchantInfoVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;

@RestController("userShopController")
@RequestMapping("/user/shop")
@Tag(name = "店铺相关接口")
@RequiredArgsConstructor
public class ShopController {

    public static final String KEY = "SHOP_STATUS";
    public static final String KEY_PHONE = "SHOP_PHONE";

    /**
     * 店铺电话默认值；生产环境可通过 Redis 键 SHOP_PHONE 覆盖
     */
    public static final String DEFAULT_PHONE = "13800138000";

    private final RedisTemplate<String, Integer> redisTemplate;
    private final RedisTemplate<String, Object> redisTemplateObject;

    /**
     * 获取店铺的营业状态
     * @return
     */
    @GetMapping("/status")
    @Operation(summary = "获取店铺的营业状态")
    public Result<Integer> getStatus(){
        Integer status = redisTemplate.opsForValue().get(KEY);
        return Result.success(status);
    }

    /**
     * 获取店铺信息（商家电话）
     * @return
     */
    @GetMapping("/getMerchantInfo")
    @Operation(summary = "获取店铺信息")
    public Result<MerchantInfoVO> getMerchantInfo(){
        Object phone = redisTemplateObject.opsForValue().get(KEY_PHONE);
        MerchantInfoVO vo = MerchantInfoVO.builder()
                .phone(phone == null ? DEFAULT_PHONE : phone.toString())
                .build();
        return Result.success(vo);
    }
}
