package com.sky.order.mapper;

/**
 * 订单明细条数的投影(列表展示"共 N 件"用)。
 *
 * <p>用一次性 GROUP BY 批量取,避免每行一次 count 的 N+1。
 */
public class OrderItemCount {

    private Long orderId;
    private Integer itemCount;

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public Integer getItemCount() {
        return itemCount;
    }

    public void setItemCount(Integer itemCount) {
        this.itemCount = itemCount;
    }
}
