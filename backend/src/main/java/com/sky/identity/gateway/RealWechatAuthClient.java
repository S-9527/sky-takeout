package com.sky.identity.gateway;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.sky.common.error.BusinessException;
import com.sky.identity.domain.IdentityErrorCode;

/**
 * 真实微信登录:调用 {@code sns/jscode2session}。
 *
 * <p>只有在 {@code sky.wechat.mock=false} 时才会被装配,所以本地联调不需要小程序密钥;
 * 而一旦显式关掉 mock 却没配密钥,就在启动时直接失败——把配置错误暴露在启动阶段,
 * 而不是等第一个用户点击登录才发现。
 */
@Component
@ConditionalOnProperty(name = "sky.wechat.mock", havingValue = "false")
public class RealWechatAuthClient implements WechatAuthClient {

    private static final String BASE_URL = "https://api.weixin.qq.com";
    private static final String CODE2SESSION_PATH = "/sns/jscode2session";

    private final WechatProperties properties;
    private final RestClient restClient;

    public RealWechatAuthClient(WechatProperties properties) {
        if (!StringUtils.hasText(properties.appId()) || !StringUtils.hasText(properties.appSecret())) {
            throw new IllegalStateException(
                    "sky.wechat.mock=false 时必须配置 sky.wechat.app-id 与 sky.wechat.app-secret");
        }
        this.properties = properties;
        this.restClient = RestClient.builder().baseUrl(BASE_URL).build();
    }

    @Override
    public String exchangeOpenid(String loginCode) {
        WechatSession session;
        try {
            session = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(CODE2SESSION_PATH)
                            .queryParam("appid", properties.appId())
                            .queryParam("secret", properties.appSecret())
                            .queryParam("js_code", loginCode)
                            .queryParam("grant_type", "authorization_code")
                            .build())
                    .retrieve()
                    .body(WechatSession.class);
        } catch (RestClientException ex) {
            throw new BusinessException(IdentityErrorCode.CUSTOMER_WECHAT_API_ERROR, "调用微信接口失败");
        }

        if (session == null) {
            throw new BusinessException(IdentityErrorCode.CUSTOMER_WECHAT_API_ERROR);
        }
        if (session.errcode() != null && session.errcode() != 0) {
            // 40029 = code 无效;40163 = code 已被使用。两者对用户而言都是"重新拿一次 code"
            throw new BusinessException(IdentityErrorCode.CUSTOMER_WECHAT_CODE_INVALID, session.errmsg());
        }
        if (!StringUtils.hasText(session.openid())) {
            throw new BusinessException(IdentityErrorCode.CUSTOMER_WECHAT_API_ERROR, "微信未返回 openid");
        }
        return session.openid();
    }

    /** 只取需要的字段;{@code session_key} 用不到,忽略未知字段即可。 */
    private record WechatSession(String openid, Integer errcode, String errmsg) {
    }
}
