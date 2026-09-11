package com.sky.identity.api.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CustomerProfileUpdateRequest(
        @Size(min = 1, max = 64, message = "昵称长度需在 1~64 位之间")
        String nickname,

        @Size(max = 255, message = "头像地址过长")
        String avatarUrl,

        @Pattern(regexp = "^$|^1[3-9][0-9]{9}$", message = "手机号格式不正确")
        String phone
) {
}
