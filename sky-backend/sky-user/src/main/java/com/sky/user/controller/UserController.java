package com.sky.user.controller;

import com.sky.user.dto.UserLoginDTO;
import com.sky.result.Result;
import com.sky.user.service.UserService;
import com.sky.user.vo.UserLoginVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/user/user")
@Tag(name = "C端用户相关接口")
@Slf4j
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * 微信登录
     * @param userLoginDTO
     * @return
     */
    @PostMapping("/login")
    @Operation(summary = "微信登录")
    public Result<UserLoginVO> login(@RequestBody UserLoginDTO userLoginDTO){
        log.info("微信用户登录：{}",userLoginDTO.getCode());

        return Result.success(userService.wxLogin(userLoginDTO));
    }
}
