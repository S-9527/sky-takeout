package com.sky.shop.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 店铺信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantInfoVO implements Serializable {

    //店铺联系电话
    private String phone;
}