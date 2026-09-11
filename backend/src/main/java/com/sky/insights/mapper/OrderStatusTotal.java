package com.sky.insights.mapper;

/** 报表聚合投影(OrderStatusTotal)。用 POJO 而不是 record:MyBatis 的 XML 结果映射靠 setter 自动装配,最稳。 */
public class OrderStatusTotal {

    private String status;
    private long total;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

}
