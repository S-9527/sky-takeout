package com.sky.payment.api;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

import com.sky.common.domain.PageQuery;
import com.sky.common.domain.PageResponse;
import com.sky.common.domain.SortSpec;
import com.sky.payment.api.dto.RefundCreateRequest;
import com.sky.payment.api.dto.RefundResponse;
import com.sky.payment.domain.RefundStatus;
import com.sky.payment.service.PaymentService;

/** 管理端退款:{@code /api/v1/admin/refunds/**} 由 SecurityConfig 限制为 ADMIN。 */
@RestController
@RequestMapping("/api/v1/admin/refunds")
public class RefundController {

    private final PaymentService paymentService;

    public RefundController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping
    public PageResponse<RefundResponse> page(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) RefundStatus status,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String refundNo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate beginDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        SortSpec sortSpec = SortSpec.parse(sort, PaymentService.REFUND_SORT_WHITELIST, "createdAt", true);
        return paymentService.pageRefunds(status, orderNo, refundNo, beginDate, endDate,
                        PageQuery.of(page, pageSize), sortSpec)
                .map(RefundResponse::from);
    }

    /** 整单全额退;订单必须已支付且未完成,金额由服务端取订单实付。 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RefundResponse create(@Valid @RequestBody RefundCreateRequest request) {
        return RefundResponse.from(paymentService.createRefund(
                request.orderNo(), request.reason(),
                request.reasonType() == null ? null : request.reasonType().name(),
                request.expectedAmountCents()));
    }
}
