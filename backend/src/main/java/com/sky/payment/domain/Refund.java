package com.sky.payment.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

import com.sky.common.persistence.AuditableEntity;

/**
 * 退款流水(D6)。v2 只覆盖"整单全额退"(领域文档 §7),但仍独立成表以支持失败重试与回调重放。
 */
@Getter
@Setter
@TableName("refund")
public class Refund extends AuditableEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private Long paymentId;

    /** 退款单号,全局唯一。 */
    private String refundNo;

    private Long amountCents;

    private RefundStatus status;

    private String reason;

    private RefundReasonType reasonType;

    private LocalDateTime refundedAt;

    private String rawNotify;
}
