package com.sky.profile.api.dto;

import java.time.OffsetDateTime;

import com.sky.common.util.Times;
import com.sky.profile.domain.UserAddress;

/** 对外的地址。对应 openapi 的 {@code UserAddress}。 */
public record UserAddressResponse(
        Long id,
        Long customerId,
        String consignee,
        String phone,
        String province,
        String city,
        String district,
        String detail,
        String label,
        Integer isDefault,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public static UserAddressResponse from(UserAddress address) {
        return new UserAddressResponse(
                address.getId(),
                address.getCustomerId(),
                address.getConsignee(),
                address.getPhone(),
                address.getProvince(),
                address.getCity(),
                address.getDistrict(),
                address.getDetail(),
                address.getLabel(),
                Boolean.TRUE.equals(address.getIsDefault()) ? 1 : 0,
                Times.toOffset(address.getCreatedAt()),
                Times.toOffset(address.getUpdatedAt()));
    }
}
