package com.sky.common.web;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;

import com.sky.common.error.BusinessException;
import com.sky.common.error.CommonErrorCode;
import com.sky.common.error.ErrorResponse;

/**
 * {@code ?ids=1,2,3} 这类批量入参的解析。
 *
 * <p>刻意不用 Spring 的 {@code List<Long>} 自动转换:那会接受 {@code -1}、也不校验整体格式,
 * 与 openapi 写明的 {@code ^[0-9]+(,[0-9]+)*$} 不一致。批量接口的入参是最容易被注入尝试的地方,
 * 格式校验必须与契约一致。
 */
public final class IdsParam {

    private static final Pattern PATTERN = Pattern.compile("^[0-9]+(,[0-9]+)*$");

    private IdsParam() {
    }

    /** 解析逗号分隔的正整数 id 列表,去除重复并保持顺序。 */
    public static List<Long> parse(String raw) {
        if (raw == null || !PATTERN.matcher(raw).matches()) {
            throw new BusinessException(CommonErrorCode.COMMON_VALIDATION_FAILED,
                    "ids 格式为逗号分隔的正整数",
                    List.of(new ErrorResponse.Detail("ids", "格式应为 1,2,3")));
        }
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        for (String part : raw.split(",")) {
            try {
                ids.add(Long.valueOf(part));
            } catch (NumberFormatException ex) {
                // 位数超过 Long 范围:正则挡不住,也必须回 400 而不是 500
                throw new BusinessException(CommonErrorCode.COMMON_VALIDATION_FAILED,
                        "ids 超出取值范围",
                        List.of(new ErrorResponse.Detail("ids", "id 超出取值范围")));
            }
        }
        return List.copyOf(ids);
    }
}
