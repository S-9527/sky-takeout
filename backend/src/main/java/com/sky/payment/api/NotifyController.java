package com.sky.payment.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sky.common.error.BusinessException;
import com.sky.common.util.Json;
import com.sky.payment.service.NotifyService;

import java.util.Map;

/**
 * 微信支付/退款结果回调。
 *
 * <p>两个刻意的偏离常规:
 * <ul>
 *   <li><b>应答体不是 v2 统一错误体</b>:微信要求 {@code {code, message}},失败要 500 + {@code FAIL}
 *       才会重试。所以这里自己接住异常,不交给 {@code GlobalExceptionHandler};</li>
 *   <li><b>直接收原始 body</b>({@code String})而不是先反序列化成 DTO:签名要对**原始字节**校验,
 *       先解析再验签等于把校验对象换成了"解析后的结果"。</li>
 * </ul>
 * 鉴权不靠 Bearer:该前缀在 SecurityConfig 里放行,鉴权交给平台签名校验。
 */
@RestController
@RequestMapping("/api/v1/notify/wechat")
public class NotifyController {

    private static final String HEADER_SIGNATURE = "Wechatpay-Signature";
    private static final String HEADER_TIMESTAMP = "Wechatpay-Timestamp";
    private static final String HEADER_NONCE = "Wechatpay-Nonce";

    private final NotifyService notifyService;

    public NotifyController(NotifyService notifyService) {
        this.notifyService = notifyService;
    }

    @PostMapping("/pay")
    public ResponseEntity<Map<String, String>> pay(
            @RequestHeader(value = HEADER_SIGNATURE, required = false) String signature,
            @RequestHeader(value = HEADER_TIMESTAMP, required = false) String timestamp,
            @RequestHeader(value = HEADER_NONCE, required = false) String nonce,
            @RequestBody(required = false) String rawBody) {
        return handle(() -> notifyService.handlePayNotify(signature, timestamp, nonce, rawBody,
                eventType(rawBody), ciphertext(rawBody), algorithm(rawBody), resourceNonce(rawBody),
                associatedData(rawBody)));
    }

    @PostMapping("/refund")
    public ResponseEntity<Map<String, String>> refund(
            @RequestHeader(value = HEADER_SIGNATURE, required = false) String signature,
            @RequestHeader(value = HEADER_TIMESTAMP, required = false) String timestamp,
            @RequestHeader(value = HEADER_NONCE, required = false) String nonce,
            @RequestBody(required = false) String rawBody) {
        return handle(() -> notifyService.handleRefundNotify(signature, timestamp, nonce, rawBody,
                eventType(rawBody), ciphertext(rawBody), algorithm(rawBody), resourceNonce(rawBody),
                associatedData(rawBody)));
    }

    private interface Handler {
        boolean handle();
    }

    /** 业务错误按状态码分流:报文/签名问题(400)= 微信不该重试;其余(含未预期异常)= 500 让它重试。 */
    private static ResponseEntity<Map<String, String>> handle(Handler handler) {
        try {
            handler.handle();
            return ack(HttpStatus.OK, "SUCCESS", "成功");
        } catch (BusinessException ex) {
            boolean clientFault = ex.errorCode().httpStatus().is4xxClientError();
            return ack(clientFault ? HttpStatus.BAD_REQUEST : HttpStatus.INTERNAL_SERVER_ERROR,
                    "FAIL", ex.getMessage());
        } catch (RuntimeException ex) {
            return ack(HttpStatus.INTERNAL_SERVER_ERROR, "FAIL", "处理失败");
        }
    }

    private static ResponseEntity<Map<String, String>> ack(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(Map.of("code", code, "message", message));
    }

    private static String eventType(String rawBody) {
        return field(rawBody, "event_type");
    }

    private static String ciphertext(String rawBody) {
        return resourceField(rawBody, "ciphertext");
    }

    private static String algorithm(String rawBody) {
        return resourceField(rawBody, "algorithm");
    }

    private static String resourceNonce(String rawBody) {
        return resourceField(rawBody, "nonce");
    }

    private static String associatedData(String rawBody) {
        return resourceField(rawBody, "associated_data");
    }

    @SuppressWarnings("unchecked")
    private static String resourceField(String rawBody, String key) {
        Object resource = parsed(rawBody).get("resource");
        if (resource instanceof Map<?, ?> map) {
            Object value = ((Map<String, Object>) map).get(key);
            return value == null ? null : String.valueOf(value);
        }
        return null;
    }

    private static String field(String rawBody, String key) {
        Object value = parsed(rawBody).get(key);
        return value == null ? null : String.valueOf(value);
    }

    /** 外层报文解析失败时返回空 Map:真正的失败会在验签/解密阶段以契约错误码暴露。 */
    private static Map<String, Object> parsed(String rawBody) {
        try {
            return Json.readMap(rawBody);
        } catch (RuntimeException ex) {
            return Map.of();
        }
    }
}
