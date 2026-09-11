package com.sky.cart.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 口味选择的归一化键,用于购物车唯一约束
 * {@code (customer_id, item_type, dish_ref_id, setmeal_ref_id, flavor_key)}。
 *
 * <p>两条要求:
 * <ul>
 *   <li><b>与选择顺序无关</b>:顾客先选辣度再选忌口,和反过来,必须落到同一行;</li>
 *   <li><b>无歧义</b>:不能把 {@code (a, b=c)} 和 {@code (a=b, c)} 编码成同一个字符串。</li>
 * </ul>
 * 所以先把每个选择编码成"长度前缀 + 原文"(含长度就能避免分隔符歧义),按字典序排序后用
 * {@code &} 连接,再取 SHA-256 十六进制(正好 64 字符,与列宽一致)。
 *
 * <p>未选口味时返回**固定空串**,与列默认值一致——不能用 hash(""),否则"未选"和"选了空"会分不清。
 */
public final class FlavorKeys {

    private FlavorKeys() {
    }

    public static String of(List<FlavorChoice> choices) {
        if (choices == null || choices.isEmpty()) {
            return "";
        }
        String canonical = choices.stream()
                .map(choice -> choice.name().length() + ":" + choice.name()
                        + choice.option().length() + ":" + choice.option())
                .sorted()
                .collect(Collectors.joining("&"));
        return sha256Hex(canonical);
    }

    private static String sha256Hex(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("JVM 必须支持 SHA-256", ex);
        }
    }
}
