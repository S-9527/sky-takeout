package com.sky.order.domain;

import org.springframework.http.HttpStatus;

import com.sky.common.error.ErrorCode;

/** 订单上下文错误码。取值与 {@code docs/03-api.md} 的 ORDER_* 表逐字一致。 */
public enum OrderErrorCode implements ErrorCode {

    /** 不存在、或不属于当前顾客(R9);商家侧则不区分是否存在。 */
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "订单不存在"),

    ORDER_CART_EMPTY(HttpStatus.UNPROCESSABLE_ENTITY, "购物车是空的,请先选择商品"),
    ORDER_SHOP_CLOSED(HttpStatus.UNPROCESSABLE_ENTITY, "门店已打烊,暂时无法下单"),
    ORDER_ITEM_NOT_ON_SALE(HttpStatus.UNPROCESSABLE_ENTITY, "有商品已下架,请刷新购物车"),
    ORDER_PRICE_CHANGED(HttpStatus.UNPROCESSABLE_ENTITY, "商品价格已变化,请刷新后重新提交"),
    ORDER_ADDRESS_INVALID(HttpStatus.UNPROCESSABLE_ENTITY, "收货地址无效,请重新选择"),

    /** R10:状态迁移只能经状态机,非法迁移一律这个码。 */
    ORDER_INVALID_TRANSITION(HttpStatus.UNPROCESSABLE_ENTITY, "订单当前状态不允许该操作"),
    ORDER_CANNOT_CANCEL(HttpStatus.UNPROCESSABLE_ENTITY, "订单当前状态不可取消"),

    ORDER_ALREADY_PAID(HttpStatus.UNPROCESSABLE_ENTITY, "订单已支付"),
    ORDER_PAY_TIMEOUT(HttpStatus.UNPROCESSABLE_ENTITY, "订单已超时关闭,请重新下单"),

    ORDER_URGE_NOT_ALLOWED(HttpStatus.UNPROCESSABLE_ENTITY, "当前订单状态不支持催单"),
    ORDER_URGE_TOO_FREQUENT(HttpStatus.CONFLICT, "已提醒商家,请勿重复催单"),
    ORDER_DUPLICATE_SUBMIT(HttpStatus.CONFLICT, "订单正在处理中,请勿重复提交"),

    ORDER_STATUS_COUNT_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "数据加载失败,请稍后重试");

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    OrderErrorCode(HttpStatus httpStatus, String defaultMessage) {
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
