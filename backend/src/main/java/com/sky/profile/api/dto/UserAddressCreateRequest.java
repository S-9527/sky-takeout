package com.sky.profile.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 新增地址。对应 openapi 的 {@code UserAddressCreateRequest}。
 *
 * <p>{@code isDefault} 用 {@code Integer}(契约里是 1/0),取值校验在控制器里做:
 * 非法值要落到统一错误体上,而不是被 Jackson 直接判成报文不可读。
 */
public record UserAddressCreateRequest(
        @NotBlank(message = "收货人不能为空")
        @Size(max = 32, message = "收货人最长 32 位")
        String consignee,

        @NotBlank(message = "手机号不能为空")
        @Pattern(regexp = "^1[3-9][0-9]{9}$", message = "手机号格式不正确")
        String phone,

        @NotBlank(message = "省份不能为空")
        @Size(max = 32, message = "省份最长 32 位")
        String province,

        @NotBlank(message = "城市不能为空")
        @Size(max = 32, message = "城市最长 32 位")
        String city,

        @NotBlank(message = "区县不能为空")
        @Size(max = 32, message = "区县最长 32 位")
        String district,

        @NotBlank(message = "详细地址不能为空")
        @Size(max = 255, message = "详细地址最长 255 位")
        String detail,

        @Size(max = 16, message = "标签最长 16 位")
        String label,

        Integer isDefault
) {
}
