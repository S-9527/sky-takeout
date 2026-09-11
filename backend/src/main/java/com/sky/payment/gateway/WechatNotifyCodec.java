package com.sky.payment.gateway;

/**
 * 微信支付回调的签名校验与报文解密(支付与退款回调共用)。
 *
 * <p>抽成端口的原因和支付渠道一样:本地联调不能依赖微信平台的证书与 APIv3 密钥,
 * 而真实实现缺配置时应该在**启动阶段**说清楚,而不是等第一次回调。
 */
public interface WechatNotifyCodec {

    /**
     * 校验平台签名。
     *
     * @throws com.sky.common.error.BusinessException 400 {@code PAY_NOTIFY_SIGNATURE_INVALID}
     */
    void verifySignature(String signature, String timestamp, String nonce, String rawBody);

    /**
     * 解密 {@code resource},返回业务报文明文(JSON)。
     *
     * @throws com.sky.common.error.BusinessException 400 {@code PAY_NOTIFY_DECRYPT_FAILED}
     */
    String decrypt(String algorithm, String ciphertext, String nonce, String associatedData);

    /** 业务报文明文(JSON)里取出的字段。 */
    record DecryptedNotify(String outTradeNo, String transactionId, Long amountCents, String tradeState,
                           String outRefundNo, String refundStatus) {
    }
}
