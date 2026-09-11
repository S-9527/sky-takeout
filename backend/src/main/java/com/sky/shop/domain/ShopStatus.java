package com.sky.shop.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalTime;

import com.sky.common.domain.EnableStatus;
import com.sky.common.persistence.AuditableEntity;

/**
 * 店铺营业状态:单行配置,{@code id} 固定为 1(领域文档 §3.11)。
 *
 * <p>打烊是**显式状态**,不由 {@code openTime}/{@code closeTime} 推导——那两个字段只作展示,
 * 顾客端据此知道"今天几点开门",而"现在能不能下单"只取决于 {@code isOpen}(R1)。
 */
@Getter
@Setter
@TableName("shop_status")
public class ShopStatus extends AuditableEntity {

    /** 单行配置的主键,恒定。 */
    public static final Long SINGLETON_ID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 列 {@code is_open}:1 营业中 / 0 已打烊。二值标记复用共享的 {@link EnableStatus}。 */
    @TableField("is_open")
    private EnableStatus isOpen;

    /** 每日开店时间,仅展示。 */
    private LocalTime openTime;

    /** 每日打烊时间,仅展示。 */
    private LocalTime closeTime;

    /** 门店公告。 */
    private String notice;

    /** null 视为未初始化(不营业):缺失行本来就会在服务层被拦成 404,这里不再猜。 */
    public boolean isOpen() {
        return isOpen != null && isOpen.isEnabled();
    }
}
