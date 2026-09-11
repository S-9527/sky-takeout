package com.sky.payment.domain;

import org.springframework.http.HttpStatus;

import com.sky.common.error.ErrorCode;

/** 支付与退款错误码。取值与 {@code docs/03-api.md} 的 PAY_* 表逐字一致。 */
public enum PaymentErrorCode implements ErrorCode {

    PAY_NOT_FOUND(HttpStatus.NOT_FOUND, "支付记录不存在"),

    PAY_ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "订单不存在"),
    PAY_ORDER_NOT_PAYABLE(HttpStatus.UNPROCESSABLE_ENTITY, "订单当前状态无法支付"),
    PAY_ORDER_NOT_PAID(HttpStatus.UNPROCESSABLE_ENTITY, "订单未支付,无法退款"),
    PAY_ORDER_NOT_REFUNDABLE(HttpStatus.UNPROCESSABLE_ENTITY, "订单已完成,不支持退款"),

    PAY_AMOUNT_MISMATCH(HttpStatus.UNPROCESSABLE_ENTITY, "支付金额与订单不符,请刷新后重试"),
    PAY_DUPLICATE_PAYMENT(HttpStatus.CONFLICT, "订单已支付,请勿重复支付"),

    PAY_REFUND_NOT_FOUND(HttpStatus.NOT_FOUND, "退款记录不存在"),
    PAY_REFUND_AMOUNT_EXCEEDED(HttpStatus.UNPROCESSABLE_ENTITY, "退款金额不正确"),
    PAY_REFUND_ALREADY_EXISTS(HttpStatus.CONFLICT, "该订单已申请退款"),

    /** R6:渠道受理失败时订单保持原状态并告警,不允许"假取消"。 */
    PAY_REFUND_FAILED(HttpStatus.BAD_GATEWAY, "退款受理失败,请稍后重试"),

    PAY_NOTIFY_SIGNATURE_INVALID(HttpStatus.BAD_REQUEST, "回调签名校验失败"),
    PAY_NOTIFY_DECRYPT_FAILED(HttpStatus.BAD_REQUEST, "回调报文解密失败");

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    PaymentErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    @Override
    public String code() {
        return name();
    }

    @Override
    public HttpStatus httpStatus() {
        return httpStatus;
    }

    @Override
    public String defaultMessage() {
        return defaultMessage;
    }
}
