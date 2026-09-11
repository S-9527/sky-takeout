package com.sky.identity.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CustomerWechatLoginRequest(
        @NotBlank(message = "code 不能为空")
        @Size(max = 128, message = "code 过长")
        String code,

        @Size(max = 64, message = "昵称最长 64 位")
        String nickname,

        @Size(max = 255, message = "头像地址过长")
        String avatarUrl
) {
}
