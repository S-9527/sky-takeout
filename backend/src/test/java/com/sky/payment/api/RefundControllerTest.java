package com.sky.payment.api;

import org.junit.jupiter.api.Test;

import java.util.List;

import com.sky.common.domain.PageResponse;
import com.sky.common.error.BusinessException;
import com.sky.common.error.CommonErrorCode;
import com.sky.payment.api.dto.RefundCreateRequest;
import com.sky.payment.api.dto.RefundResponse;
import com.sky.payment.domain.Refund;
import com.sky.payment.domain.RefundReasonType;
import com.sky.payment.domain.RefundStatus;
import com.sky.payment.service.PaymentService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefundControllerTest {

    private final PaymentService paymentService = mock(PaymentService.class);
    private final RefundController controller = new RefundController(paymentService);

    private static PaymentService.RefundView refundView() {
        Refund refund = new Refund();
        refund.setId(7001L);
        refund.setOrderId(4001L);
        refund.setPaymentId(6001L);
        refund.setRefundNo("RF202501011200000001");
        refund.setAmountCents(5200L);
        refund.setStatus(RefundStatus.SUCCESS);
        refund.setReason("顾客电话要求取消");
        refund.setReasonType(RefundReasonType.CUSTOMER_APPLY);
        return new PaymentService.RefundView(refund, "202501011200000001");
    }

    @Test
    void pageMapsRefundRows() {
        when(paymentService.pageRefunds(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(PageResponse.of(List.of(refundView()), 1, 20, 1));

        PageResponse<RefundResponse> response = controller.page(1, 20, null, RefundStatus.SUCCESS,
                "202501011200000001", null, null, null);

        assertThat(response.records()).hasSize(1);
        assertThat(response.records().get(0).orderNo()).isEqualTo("202501011200000001");
        assertThat(response.records().get(0).status()).isEqualTo("SUCCESS");
        assertThat(response.records().get(0).reasonType()).isEqualTo("CUSTOMER_APPLY");
    }

    @Test
    void pageRejectsUnknownSortField() {
        assertThatThrownBy(() -> controller.page(1, 20, "id,asc", null, null, null, null, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(CommonErrorCode.COMMON_SORT_FIELD_NOT_ALLOWED));
    }

    @Test
    void createPassesRequestFieldsThrough() {
        when(paymentService.createRefund(eq("202501011200000001"), eq("顾客电话要求取消"),
                eq(RefundReasonType.CUSTOMER_APPLY), eq(5200L))).thenReturn(refundView());

        RefundResponse response = controller.create(new RefundCreateRequest(
                "202501011200000001", "顾客电话要求取消", RefundReasonType.CUSTOMER_APPLY, 5200L));

        assertThat(response.id()).isEqualTo(7001L);
        assertThat(response.amountCents()).isEqualTo(5200L);
        verify(paymentService).createRefund("202501011200000001", "顾客电话要求取消",
                RefundReasonType.CUSTOMER_APPLY, 5200L);
    }

    @Test
    void createToleratesOptionalFields() {
        when(paymentService.createRefund(any(), any(), any(), any())).thenReturn(refundView());

        controller.create(new RefundCreateRequest("202501011200000001", "原因", null, null));

        verify(paymentService).createRefund("202501011200000001", "原因", null, null);
    }
}
