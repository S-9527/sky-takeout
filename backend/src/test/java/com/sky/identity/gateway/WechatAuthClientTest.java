package com.sky.identity.gateway;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WechatAuthClientTest {

    private final MockWechatAuthClient mockClient = new MockWechatAuthClient();

    /** 同一个 code 必须永远得到同一个 openid,否则重启服务就会凭空多出一堆顾客。 */
    @Test
    void mockClientMapsCodeDeterministically() {
        String first = mockClient.exchangeOpenid("code-1");

        assertThat(mockClient.exchangeOpenid("code-1")).isEqualTo(first);
        assertThat(mockClient.exchangeOpenid("code-2")).isNotEqualTo(first);
    }

    @Test
    void mockClientProducesSafeOpenidShape() {
        String openid = mockClient.exchangeOpenid("任意长度或含特殊字符的 code !@#$%^&*()");

        assertThat(openid).startsWith("mock_").hasSizeLessThanOrEqualTo(64).matches("mock_[0-9a-f]{32}");
    }

    @Test
    void mockClientTreatsMissingCodeAsAnonymous() {
        String anonymous = mockClient.exchangeOpenid(null);

        assertThat(mockClient.exchangeOpenid("   ")).isEqualTo(anonymous);
        assertThat(anonymous).startsWith("mock_");
    }

    /** 关掉 mock 却没配密钥时快速失败,而不是等第一个用户点登录才报错。 */
    @Test
    void realClientRefusesToStartWithoutCredentials() {
        assertThatThrownBy(() -> new RealWechatAuthClient(new WechatProperties("", "", false)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sky.wechat.app-id");

        assertThatThrownBy(() -> new RealWechatAuthClient(new WechatProperties("app-id", "  ", false)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void realClientConstructsWithoutCallingWechat() {
        assertThatCode(() -> new RealWechatAuthClient(new WechatProperties("app-id", "app-secret", false)))
                .doesNotThrowAnyException();
    }
}
