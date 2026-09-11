package com.sky.identity.api.dto;

import java.time.OffsetDateTime;

import com.sky.common.util.Times;
import com.sky.identity.domain.Customer;

/** 对外的顾客资料。不含 openid:那是内部身份标识,前端拿不到也不需要。 */
public record CustomerResponse(
        Long id,
        String nickname,
        String avatarUrl,
        String phone,
        Integer status,
        OffsetDateTime lastLoginAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        Long createdBy,
        Long updatedBy
) {

    public static CustomerResponse from(Customer customer) {
        return new CustomerResponse(
                customer.getId(),
                customer.getNickname(),
                customer.getAvatarUrl(),
                customer.getPhone(),
                customer.getStatus() == null ? null : customer.getStatus().getValue(),
                Times.toOffset(customer.getLastLoginAt()),
                Times.toOffset(customer.getCreatedAt()),
                Times.toOffset(customer.getUpdatedAt()),
                customer.getCreatedBy(),
                customer.getUpdatedBy());
    }
}
