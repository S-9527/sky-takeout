package com.sky.identity.gateway;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 本地开发默认实现:不调微信接口,把 loginCode 确定性地映射成一个稳定 openid。
 *
 * <p>同一个 code 永远得到同一个 openid,所以重启服务不会凭空多出一堆顾客;
 * 不同 code 得到不同 openid,方便用不同 code 模拟多个用户。
 */
@Component
@ConditionalOnProperty(name = "sky.wechat.mock", havingValue = "true", matchIfMissing = true)
public class MockWechatAuthClient implements WechatAuthClient {

    private static final String PREFIX = "mock_";

    @Override
    public String exchangeOpenid(String loginCode) {
        String normalized = loginCode == null || loginCode.isBlank() ? "anonymous" : loginCode;
        // 用名字型 UUID(内部是确定性摘要)而不是直接拼接:openid 列只有 64 字符,
        // 任意长度的 code 直接拼进去可能撑爆,而且会出现含非法字符的值。
        String digest = UUID.nameUUIDFromBytes(normalized.getBytes(StandardCharsets.UTF_8))
                .toString()
                .replace("-", "");
        return PREFIX + digest;
    }
}
