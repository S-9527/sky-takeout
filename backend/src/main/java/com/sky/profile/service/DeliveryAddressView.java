package com.sky.profile.service;

/**
 * 收货地址的**服务层跨上下文契约**:下单时订单要把它整体快照进 {@code orders}
 * (领域文档 §3.8 的地址快照),但又不能引用 {@code UserAddress} 实体(架构规则 L4)。
 *
 * @param id 仅作溯源写入 {@code source_address_id}
 */
public record DeliveryAddressView(
        Long id,
        String consignee,
        String phone,
        String province,
        String city,
        String district,
        String detail
) {
}
