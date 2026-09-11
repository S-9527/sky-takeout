package com.sky.payment.gateway;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.sky.common.error.BusinessException;
import com.sky.payment.domain.PaymentErrorCode;

/**
 * 本地联调用的回调编解码器:签名是一个固定字符串,{@code ciphertext} 就是**明文 JSON**。
 *
 * <p>刻意不做"假解密"来伪装成真实实现:mock 通道本来就不经过微信,回调报文由本地构造,
 * 所以这里把 {@code ciphertext} 直接当明文读,并在文档里写明。真实通道(APIv3 密钥 + 平台证书)
 * **尚未实现**;把 {@code sky.payment.gateway} 配成 {@code wechat} 时,应用会因为找不到
 * {@link WechatNotifyCodec} 而在启动阶段失败——这是刻意的失败方向,不允许"配了真实通道、实际走模拟"。
 */
@Component
@ConditionalOnProperty(name = "sky.payment.gateway", havingValue = "mock", matchIfMissing = true)
public class MockWechatNotifyCodec implements WechatNotifyCodec {

    /** 本地约定的签名值,写在 README / smoke 脚本里,便于直接构造回调请求。 */
    public static final String MOCK_SIGNATURE = "mock-signature";

    @Override
    public void verifySignature(String signature, String timestamp, String nonce, String rawBody) {
        if (!MOCK_SIGNATURE.equals(signature)) {
            throw new BusinessException(PaymentErrorCode.PAY_NOTIFY_SIGNATURE_INVALID);
        }
    }

    @Override
    public String decrypt(String algorithm, String ciphertext, String nonce, String associatedData) {
        if (ciphertext == null || ciphertext.isBlank()) {
            throw new BusinessException(PaymentErrorCode.PAY_NOTIFY_DECRYPT_FAILED);
        }
        return ciphertext;
    }
}
