package com.sky.payment.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

import com.sky.common.persistence.AuditableEntity;

/**
 * 支付流水(D6:独立成表)。
 *
 * <p>旧 schema 把支付信息塞在订单表的几个列里,导致一个订单无法表达"部分退款""重复支付回调""退款失败重试"。
 * {@code transaction_id} 上有唯一键,它就是支付回调的幂等键——唯一索引允许多个 NULL,
 * 所以"多笔未完成的支付尝试"可以共存,而同一个平台单号只能落一行。
 */
@Getter
@Setter
@TableName("payment")
public class Payment extends AuditableEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    /** 冗余业务单号:回调只带单号时用它定位订单。 */
    private String orderNo;

    private PaymentChannel channel;

    private PaymentStatus status;

    private Long amountCents;

    /** 支付平台单号,唯一(幂等键);未成功为 NULL。 */
    private String transactionId;

    private String prepayId;

    private LocalDateTime paidAt;

    /** 原始回调报文(JSON 文本),排障留痕。 */
    private String rawNotify;
}
