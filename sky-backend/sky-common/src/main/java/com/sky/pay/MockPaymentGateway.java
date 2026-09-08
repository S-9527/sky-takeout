package com.sky.pay;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 本地 Mock 支付网关：开发环境不依赖微信商户证书即可走通支付链路，
 * 支付调用时直接确认支付成功。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "sky.pay.gateway", havingValue = "mock")
public class MockPaymentGateway implements PaymentGateway {

    @Override
    public JSONObject pay(String orderNum, BigDecimal total, String description, String openid) throws Exception {
        log.info("[Mock支付] 生成预支付交易单, orderNum={}, amount={}", orderNum, total);
        JSONObject jo = new JSONObject();
        jo.put("timeStamp", String.valueOf(System.currentTimeMillis() / 1000));
        jo.put("nonceStr", "mock_nonce");
        jo.put("package", "prepay_id=mock_prepay_id");
        jo.put("signType", "RSA");
        jo.put("paySign", "mock_sign");
        return jo;
    }

    @Override
    public String refund(String outTradeNo, String outRefundNo, BigDecimal refund, BigDecimal total) throws Exception {
        log.info("[Mock支付] 申请退款, outTradeNo={}, refund={}", outTradeNo, refund);
        return "SUCCESS";
    }

    @Override
    public boolean autoConfirmOnPay() {
        return true;
    }
}