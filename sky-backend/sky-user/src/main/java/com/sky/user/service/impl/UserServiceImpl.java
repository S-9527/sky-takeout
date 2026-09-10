package com.sky.user.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sky.constant.MessageConstant;
import com.sky.user.dto.UserLoginDTO;
import com.sky.user.entity.User;
import com.sky.user.mapper.UserMapper;
import com.sky.properties.WeChatProperties;
import com.sky.user.service.UserService;
import com.sky.user.vo.UserLoginVO;
import com.sky.auth.token.JwtTokenService;
import com.sky.auth.token.TokenPair;
import com.sky.auth.token.TokenType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    //微信服务接口地址
    public static final String WX_LOGIN = "https://api.weixin.qq.com/sns/jscode2session";

    private final WeChatProperties weChatProperties;
    private final UserMapper userMapper;
    private final RestClient.Builder restClientBuilder;
    private final JwtTokenService jwtTokenService;

    /**
     * 微信登录
     * @param userLoginDTO
     * @return
     */
    public UserLoginVO wxLogin(UserLoginDTO userLoginDTO) {
        String openid = getOpenid(userLoginDTO.getCode());

        //判断openid是否为空，如果为空表示登录失败，抛出业务异常
        if(openid == null){
            throw new InternalAuthenticationServiceException(MessageConstant.LOGIN_FAILED);
        }

        //判断当前用户是否为新用户
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getOpenid, openid));

        //如果是新用户，自动完成注册
        if(user == null){
            user = User.builder()
                    .openid(openid)
                    .createTime(LocalDateTime.now())
                    .build();
            userMapper.insert(user);
        }

        //签发jwt令牌并返回登录结果
        TokenPair tokenPair = jwtTokenService.createTokenPair(TokenType.USER, user.getId());
        return UserLoginVO.builder()
                .id(user.getId())
                .openid(user.getOpenid())
                .accessToken(tokenPair.getAccessToken())
                .refreshToken(tokenPair.getRefreshToken())
                .build();
    }

    /**
     * 刷新令牌对：校验并轮换刷新令牌，返回新的访问+刷新令牌
     *
     * @param refreshToken 刷新令牌
     * @return 新的令牌对
     */
    public TokenPair refresh(String refreshToken) {
        return jwtTokenService.refresh(TokenType.USER, refreshToken);
    }

    /**
     * 退出登录：同时撤销访问令牌与刷新令牌
     *
     * @param accessToken  访问令牌
     * @param refreshToken 刷新令牌(可为空)
     */
    public void logout(String accessToken, String refreshToken) {
        if (accessToken != null && !accessToken.isEmpty()) {
            jwtTokenService.revokeToken(accessToken, TokenType.USER);
        }
        if (refreshToken != null && !refreshToken.isEmpty()) {
            jwtTokenService.revokeToken(refreshToken, TokenType.USER);
        }
    }

    /**
     * 根据id查询用户
     * @param id
     * @return
     */
    public User getById(Long id) {
        return userMapper.selectById(id);
    }

    /**
     * 根据条件统计用户数量
     * @param map
     * @return
     */
    public Integer countByMap(Map map) {
        Object begin = map.get("begin");
        Object end = map.get("end");
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .gt(begin != null, User::getCreateTime, begin)
                .lt(end != null, User::getCreateTime, end);
        return userMapper.selectCount(wrapper).intValue();
    }

    /**
     * 调用微信接口服务，获取微信用户的openid
     * @param code
     * @return
     */
    private String getOpenid(String code){
        // mock 模式：不调用微信接口，用 jsCode 直接映射 openid，便于本地开发和小程序工具调试
        if ("mock".equals(weChatProperties.getLogin())){
            return "dev-" + code;
        }
        //调用微信接口服务，获得当前微信用户的openid
        Map<String, String> params = new HashMap<>();
        params.put("appid",weChatProperties.getAppid());
        params.put("secret",weChatProperties.getSecret());
        params.put("js_code",code);
        params.put("grant_type","authorization_code");
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(WX_LOGIN);
        params.forEach((key, value) -> builder.queryParam(key, value));
        String json = restClientBuilder.build().get()
                .uri(builder.build().toUri())
                .retrieve()
                .body(String.class);

        JSONObject jsonObject = JSON.parseObject(json);
        String openid = jsonObject.getString("openid");
        return openid;
    }
}
