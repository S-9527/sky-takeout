package com.sky.order.api.dto;

import com.sky.profile.service.DeliveryAddressView;

/** 结算页展示的收货地址(下单后会整体快照进订单)。 */
public record DeliveryAddressResponse(
        Long id,
        String consignee,
        String phone,
        String province,
        String city,
        String district,
        String detail
) {

    public static DeliveryAddressResponse from(DeliveryAddressView address) {
        return new DeliveryAddressResponse(address.id(), address.consignee(), address.phone(),
                address.province(), address.city(), address.district(), address.detail());
    }
}
