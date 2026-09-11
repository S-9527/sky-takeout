package com.sky.payment.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.sky.common.error.BusinessException;
import com.sky.payment.domain.PaymentErrorCode;
import com.sky.payment.service.NotifyService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 回调接口的应答形状与微信支付一致:{@code {code, message}} + SUCCESS/FAIL,
 * **不是** v2 统一错误体——所以这个测试刻意不校验 traceId 之类的字段。
 */
class NotifyControllerTest {

    private static final String BODY = "{\"id\":\"EV-1\",\"event_type\":\"TRANSACTION.SUCCESS\","
            + "\"resource\":{\"algorithm\":\"AEAD_AES_256_GCM\",\"ciphertext\":\"{}\",\"nonce\":\"n\","
            + "\"associated_data\":\"transaction\"}}";

    private final NotifyService notifyService = mock(NotifyService.class);
    private final NotifyController controller = new NotifyController(notifyService);

    @Test
    void successReturns200WithSuccessAck() {
        when(notifyService.handlePayNotify(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(true);

        var response = controller.pay("sig", "1", "nonce", BODY);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("code", "SUCCESS");
    }

    /** 签名/报文问题 → 400,微信不该重试。 */
    @Test
    void signatureFailureReturns400() {
        when(notifyService.handlePayNotify(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new BusinessException(PaymentErrorCode.PAY_NOTIFY_SIGNATURE_INVALID));

        var response = controller.pay("bad", "1", "nonce", BODY);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("code", "FAIL");
    }

    /** 未预期异常 → 500 + FAIL,让微信按策略重试。 */
    @Test
    void unexpectedFailureReturns500() {
        when(notifyService.handlePayNotify(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("db down"));

        var response = controller.pay("sig", "1", "nonce", BODY);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).containsEntry("code", "FAIL");
    }

    @Test
    void refundCallbackDelegatesAndExtractsResourceFields() {
        when(notifyService.handleRefundNotify(eq("sig"), eq("1"), eq("nonce"), any(),
                eq("TRANSACTION.SUCCESS"), eq("{}"), eq("AEAD_AES_256_GCM"), eq("n"), eq("transaction")))
                .thenReturn(true);

        var response = controller.refund("sig", "1", "nonce", BODY);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("code", "SUCCESS");
    }

    @Test
    void malformedBodyDoesNotBlowUpBeforeSignatureCheck() {
        when(notifyService.handlePayNotify(any(), any(), any(), eq("not-json"), any(), any(), any(), any(), any()))
                .thenThrow(new BusinessException(PaymentErrorCode.PAY_NOTIFY_DECRYPT_FAILED));

        var response = controller.pay("sig", "1", "nonce", "not-json");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("code", "FAIL");
    }
}
