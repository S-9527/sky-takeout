package com.sky.pay;

import com.alibaba.fastjson.JSONObject;
import com.sky.utils.WeChatPayUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 微信支付网关：默认实现，delegate 到 WeChatPayUtil。
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "sky.pay.gateway", havingValue = "wechat", matchIfMissing = true)
public class WechatPaymentGateway implements PaymentGateway {

    private final WeChatPayUtil weChatPayUtil;

    @Override
    public JSONObject pay(String orderNum, BigDecimal total, String description, String openid) throws Exception {
        return weChatPayUtil.pay(orderNum, total, description, openid);
    }

    @Override
    public String refund(String outTradeNo, String outRefundNo, BigDecimal refund, BigDecimal total) throws Exception {
        return weChatPayUtil.refund(outTradeNo, outRefundNo, refund, total);
    }
}