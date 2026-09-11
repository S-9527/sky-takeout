package com.sky.insights.mapper;

/** 报表聚合投影(DailyOrderCount)。用 POJO 而不是 record:MyBatis 的 XML 结果映射靠 setter 自动装配,最稳。 */
public class DailyOrderCount {

    private java.time.LocalDate day;
    private long totalOrderCount;
    private long validOrderCount;

    public java.time.LocalDate getDay() {
        return day;
    }

    public void setDay(java.time.LocalDate day) {
        this.day = day;
    }

    public long getTotalOrderCount() {
        return totalOrderCount;
    }

    public void setTotalOrderCount(long totalOrderCount) {
        this.totalOrderCount = totalOrderCount;
    }

    public long getValidOrderCount() {
        return validOrderCount;
    }

    public void setValidOrderCount(long validOrderCount) {
        this.validOrderCount = validOrderCount;
    }

}
