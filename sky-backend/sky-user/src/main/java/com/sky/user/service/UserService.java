package com.sky.user.service;

import com.sky.user.dto.UserLoginDTO;
import com.sky.user.entity.User;
import com.sky.user.vo.UserLoginVO;

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
}
