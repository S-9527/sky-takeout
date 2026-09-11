package com.sky.identity.gateway;

/**
 * 微信小程序登录:用一次性 loginCode 换 openid。
 *
 * <p>抽成接口是为了让本地开发不依赖微信后台:默认用 mock 实现,
 * 只要 {@code sky.wechat.mock=true}(默认),任何 code 都能换到一个稳定的 openid。
 */
public interface WechatAuthClient {

    /**
     * @param loginCode 小程序 {@code wx.login()} 拿到的一次性 code
     * @return 该微信用户的 openid
     */
    String exchangeOpenid(String loginCode);
}
