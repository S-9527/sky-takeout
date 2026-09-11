package com.sky.insights.mapper;

/** 报表聚合投影(DishSales)。用 POJO 而不是 record:MyBatis 的 XML 结果映射靠 setter 自动装配,最稳。 */
public class DishSales {

    private String name;
    private long copies;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getCopies() {
        return copies;
    }

    public void setCopies(long copies) {
        this.copies = copies;
    }

}
