package com.sky.payment.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sky.payment.api.dto.PaymentCreateRequest;
import com.sky.payment.api.dto.PaymentStartResponse;
import com.sky.payment.api.dto.PaymentStatusResponse;
import com.sky.payment.service.PaymentService;
import com.sky.security.CurrentPrincipal;

/**
 * 顾客端支付。整个前缀由 SecurityConfig 限制为 CUSTOMER;顾客 id 一律取自令牌(R9),
 * 订单归属由 order 上下文校验(他人订单一律 404)。
 */
@RestController
@RequestMapping("/api/v1/customer/orders/{orderId}/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentStartResponse start(@PathVariable Long orderId,
                                      @Valid @RequestBody PaymentCreateRequest request) {
        return PaymentStartResponse.from(paymentService.start(
                CurrentPrincipal.requireCustomerId(), orderId, request.channel(), request.payerOpenid()));
    }

    /** 供前端支付后轮询;订单尚未发起支付时 {@code payment} 为 null。 */
    @GetMapping("/status")
    public PaymentStatusResponse status(@PathVariable Long orderId) {
        return PaymentStatusResponse.from(
                paymentService.status(CurrentPrincipal.requireCustomerId(), orderId));
    }
}
