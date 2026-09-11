package com.sky.insights.mapper;

/** 报表聚合投影(DailyUserCount)。用 POJO 而不是 record:MyBatis 的 XML 结果映射靠 setter 自动装配,最稳。 */
public class DailyUserCount {

    private java.time.LocalDate day;
    private long newUserCount;

    public java.time.LocalDate getDay() {
        return day;
    }

    public void setDay(java.time.LocalDate day) {
        this.day = day;
    }

    public long getNewUserCount() {
        return newUserCount;
    }

    public void setNewUserCount(long newUserCount) {
        this.newUserCount = newUserCount;
    }

}
