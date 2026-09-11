package com.sky.order.mapper;

/** 按状态聚合的行数投影(工作台角标用,一次 GROUP BY 拿全部状态)。 */
public class OrderStatusCount {

    private String status;
    private Long total;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getTotal() {
        return total;
    }

    public void setTotal(Long total) {
        this.total = total;
    }
}
