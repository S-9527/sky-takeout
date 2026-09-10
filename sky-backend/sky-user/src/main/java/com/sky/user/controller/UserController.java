package com.sky.user.controller;

import com.sky.user.dto.UserLoginDTO;
import com.sky.result.Result;
import com.sky.user.service.UserService;
import com.sky.user.vo.UserLoginVO;
import com.sky.dto.RefreshTokenDTO;
import com.sky.token.TokenPair;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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

    /**
     * 刷新令牌对
     *
     * @return
     */
    @PostMapping("/refresh")
    @Operation(summary = "用户令牌刷新")
    public Result<TokenPair> refresh(@RequestBody RefreshTokenDTO refreshTokenDTO){
        log.info("用户令牌刷新");
        return Result.success(userService.refresh(refreshTokenDTO.getRefreshToken()));
    }

    /**
     * 退出登录
     *
     * @return
     */
    @PostMapping("/logout")
    @Operation(summary = "用户退出")
    public Result<String> logout(@RequestHeader(name = "Authorization", required = false) String authorization,
                                 @RequestBody(required = false) RefreshTokenDTO refreshTokenDTO){
        String accessToken = null;
        if (authorization != null && authorization.startsWith("Bearer ")) {
            accessToken = authorization.substring("Bearer ".length());
        }
        userService.logout(accessToken,
                refreshTokenDTO != null ? refreshTokenDTO.getRefreshToken() : null);
        return Result.success();
    }
}
