package com.sky.user.service;

import com.sky.user.dto.UserLoginDTO;
import com.sky.user.entity.User;
import com.sky.user.vo.UserLoginVO;
import com.sky.auth.token.TokenPair;

import java.util.Map;

public interface UserService {

    /**
     * 微信登录
     * @param userLoginDTO
     * @return
     */
    UserLoginVO wxLogin(UserLoginDTO userLoginDTO);

    /**
     * 根据id查询用户
     * @param id
     * @return
     */
    User getById(Long id);

    /**
     * 根据条件统计用户数量
     * @param map
     * @return
     */
    Integer countByMap(Map map);

    /**
     * 刷新令牌对：校验并轮换刷新令牌，返回新的访问+刷新令牌
     * @param refreshToken 刷新令牌
     * @return 新的令牌对
     */
    TokenPair refresh(String refreshToken);

    /**
     * 退出登录：同时撤销访问令牌与刷新令牌
     * @param accessToken  访问令牌
     * @param refreshToken 刷新令牌(可为空)
     */
    void logout(String accessToken, String refreshToken);
}
