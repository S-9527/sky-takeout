package com.sky.order.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

import com.sky.common.persistence.AuditableEntity;

/**
 * 订单主表(表名 {@code orders}:{@code order} 是 SQL 保留字)。
 *
 * <p>两张"快照"都在这张表上:
 * <ul>
 *   <li><b>地址快照</b>:{@code consignee}/{@code phone}/{@code province}/{@code city}/{@code district}/{@code detail}
 *       ——旧 schema 只存 address_id,顾客改地址后历史订单跟着变,是审计事故;</li>
 *   <li><b>状态时间戳</b>:每个状态一列,而不是语义混乱的 order_time/checkout_time/delivery_time/cancel_time。</li>
 * </ul>
 * 商品名价图与口味的快照在 {@link OrderItem} 上。金额一律整数"分"(D1)。
 */
@Getter
@Setter
@TableName("orders")
public class Order extends AuditableEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务单号,顾客可见,唯一。 */
    private String orderNo;

    private Long customerId;

    private OrderStatus status;

    private Long totalAmountCents;

    private Long packAmountCents;

    private Long deliveryAmountCents;

    /** v2 恒为 0,字段预留。 */
    private Long discountAmountCents;

    /** 实付 = total + pack + delivery - discount。 */
    private Long payAmountCents;

    private PayStatus payStatus;

    /** 未支付为空。 */
    private PayMethod payMethod;

    // ---- 地址快照 ----
    private String consignee;
    private String phone;
    private String province;
    private String city;
    private String district;
    private String detail;

    /** 下单时选用的地址 id,仅作溯源;不建外键(地址可被顾客删除)。 */
    private Long sourceAddressId;

    private String remark;

    private Integer tablewareCount;

    private LocalDateTime placedAt;

    /** v2 不做配送调度(领域文档 §7),保持为空。 */
    private LocalDateTime estimatedDeliveryAt;

    private LocalDateTime paidAt;
    private LocalDateTime acceptedAt;
    private LocalDateTime deliveringAt;
    private LocalDateTime completedAt;
    private LocalDateTime cancelledAt;

    private CancelSide cancelSide;
    private String cancelReason;

    /** 只读投影:明细条数,由查询语句填充,不是表里的列。 */
    @TableField(exist = false)
    private Integer itemCount;
}
