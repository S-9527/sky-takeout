package com.sky.pay;

import com.alibaba.fastjson.JSONObject;

import java.math.BigDecimal;

/**
 * 支付网关策略接口：屏蔽真实微信支付与本地 Mock 实现的差异。
 */
public interface PaymentGateway {

    /**
     * 生成预支付交易单
     *
     * @param orderNum    商户订单号
     * @param total       支付金额，单位 元
     * @param description 商品描述
     * @param openid      微信用户 openid
     * @return jsapi 调起支付所需参数
     */
    JSONObject pay(String orderNum, BigDecimal total, String description, String openid) throws Exception;

    /**
     * 申请退款
     *
     * @param outTradeNo  商户订单号
     * @param outRefundNo 商户退款单号
     * @param refund      退款金额，单位 元
     * @param total       原订单金额，单位 元
     * @return 支付通道返回结果
     */
    String refund(String outTradeNo, String outRefundNo, BigDecimal refund, BigDecimal total) throws Exception;

    /**
     * 是否在支付方法内直接确认支付成功（Mock 场景用于本地开发联调）
     */
    default boolean autoConfirmOnPay() {
        return false;
    }
}