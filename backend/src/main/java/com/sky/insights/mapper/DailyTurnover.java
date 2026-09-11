package com.sky.insights.mapper;

/** 报表聚合投影(DailyTurnover)。用 POJO 而不是 record:MyBatis 的 XML 结果映射靠 setter 自动装配,最稳。 */
public class DailyTurnover {

    private java.time.LocalDate day;
    private long revenueCents;
    private long orderCount;

    public java.time.LocalDate getDay() {
        return day;
    }

    public void setDay(java.time.LocalDate day) {
        this.day = day;
    }

    public long getRevenueCents() {
        return revenueCents;
    }

    public void setRevenueCents(long revenueCents) {
        this.revenueCents = revenueCents;
    }

    public long getOrderCount() {
        return orderCount;
    }

    public void setOrderCount(long orderCount) {
        this.orderCount = orderCount;
    }

}
