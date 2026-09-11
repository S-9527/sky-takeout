package com.sky.common.persistence;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 审计四件套。所有业务实体继承它。
 *
 * <p>时间列刻意用 {@link FieldStrategy#NEVER}:值**永远**由数据库的
 * {@code DEFAULT CURRENT_TIMESTAMP} / {@code ON UPDATE CURRENT_TIMESTAMP} 产生。
 * 否则"查出实体 → 改一个字段 → updateById"会把读到的旧时间写回去,数据库的自动更新形同虚设。
 *
 * <p>操作人列由 {@code AuditingMetaObjectHandler} 在插入/更新时填充。
 */
@Getter
@Setter
public abstract class AuditableEntity {

    @TableField(value = "created_at", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updatedAt;

    @TableField(value = "created_by", fill = FieldFill.INSERT)
    private Long createdBy;

    @TableField(value = "updated_by", fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;
}
