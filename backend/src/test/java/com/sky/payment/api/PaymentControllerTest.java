package com.sky.payment.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.sky.payment.api.dto.PaymentCreateRequest;
import com.sky.payment.api.dto.PaymentStartResponse;
import com.sky.payment.api.dto.PaymentStatusResponse;
import com.sky.payment.domain.PaymentChannel;
import com.sky.payment.service.PaymentService;
import com.sky.security.Audience;
import com.sky.security.CurrentPrincipal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentControllerTest {

    private static final long CUSTOMER = 7L;
    private static final long ORDER_ID = 4001L;

    private final PaymentService paymentService = mock(PaymentService.class);
    private final PaymentController controller = new PaymentController(paymentService);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private static void actingAsCustomer() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new CurrentPrincipal(CUSTOMER, Audience.CUSTOMER, "CUSTOMER"), null));
    }

    @Test
    void startMapsPaymentParams() {
        actingAsCustomer();
        when(paymentService.start(CUSTOMER, ORDER_ID, PaymentChannel.MOCK, "openid"))
                .thenReturn(new PaymentService.StartResult(6001L, "MOCK", "SUCCESS", 5200L,
                        "1735704070", "nonce", "prepay_id=mock", "RSA", "sign", null));

        PaymentStartResponse response = controller.start(ORDER_ID, new PaymentCreateRequest(PaymentChannel.MOCK, "openid"));

        assertThat(response.paymentId()).isEqualTo(6001L);
        assertThat(response.status()).isEqualTo("SUCCESS");
        assertThat(response.payAmountCents()).isEqualTo(5200L);
        assertThat(response.packageValue()).isEqualTo("prepay_id=mock");
        assertThat(response.mockPayUrl()).isNull();
        verify(paymentService).start(CUSTOMER, ORDER_ID, PaymentChannel.MOCK, "openid");
    }

    @Test
    void statusMapsLatestPaymentAndToleratesNone() {
        actingAsCustomer();
        when(paymentService.status(CUSTOMER, ORDER_ID)).thenReturn(new PaymentService.StatusView(
                ORDER_ID, "202501011200000001", "PENDING_PAYMENT", "UNPAID",
                new PaymentService.PaymentView(6001L, ORDER_ID, "202501011200000001", "MOCK", "PENDING",
                        5200L, null, "prepay", null, null)));

        PaymentStatusResponse response = controller.status(ORDER_ID);

        assertThat(response.orderNo()).isEqualTo("202501011200000001");
        assertThat(response.payStatus()).isEqualTo("UNPAID");
        assertThat(response.payment().id()).isEqualTo(6001L);
        assertThat(response.payment().status()).isEqualTo("PENDING");
    }

    @Test
    void statusWithoutPaymentReturnsNull() {
        actingAsCustomer();
        when(paymentService.status(CUSTOMER, ORDER_ID)).thenReturn(new PaymentService.StatusView(
                ORDER_ID, "202501011200000001", "PENDING_PAYMENT", "UNPAID", null));

        assertThat(controller.status(ORDER_ID).payment()).isNull();
    }
}
