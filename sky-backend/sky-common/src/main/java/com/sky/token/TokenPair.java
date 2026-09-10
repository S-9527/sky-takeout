package com.sky.token;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 登录/刷新返回的令牌对：短效访问令牌 + 长效刷新令牌。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TokenPair implements Serializable {

    private String accessToken;

    private String refreshToken;
}