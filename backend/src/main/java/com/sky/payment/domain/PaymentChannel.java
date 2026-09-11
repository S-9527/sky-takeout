package com.sky.payment.domain;

import com.baomidou.mybatisplus.annotation.IEnum;

/** 支付渠道。MOCK 仅用于本地开发(发起即成功,不需要微信商户号)。 */
public enum PaymentChannel implements IEnum<String> {

    WECHAT,
    MOCK;

    @Override
    public String getValue() {
        return name();
    }
}
