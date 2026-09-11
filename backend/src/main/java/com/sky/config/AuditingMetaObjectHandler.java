package com.sky.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;

import com.sky.security.CurrentPrincipal;

/**
 * 填充 {@code created_by} / {@code updated_by}。
 *
 * <p>放在 config 包而不是 common:它需要读安全上下文,而 common 不允许依赖 security。
 * 系统任务(定时关单等)没有登录主体,统一记为 {@link #SYSTEM_ACTOR_ID}。
 */
public class AuditingMetaObjectHandler implements MetaObjectHandler {

    /** 系统操作人 id。员工与顾客 id 都从 1 开始,0 不会被占用。 */
    public static final long SYSTEM_ACTOR_ID = 0L;

    @Override
    public void insertFill(MetaObject metaObject) {
        Long actorId = currentActorId();
        strictInsertFill(metaObject, "createdBy", Long.class, actorId);
        strictInsertFill(metaObject, "updatedBy", Long.class, actorId);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        strictUpdateFill(metaObject, "updatedBy", Long.class, currentActorId());
    }

    private Long currentActorId() {
        CurrentPrincipal principal = CurrentPrincipal.current();
        return principal == null ? SYSTEM_ACTOR_ID : principal.id();
    }
}
