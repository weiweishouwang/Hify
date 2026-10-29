package com.hify.shared.mybatis;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.hify.shared.context.CurrentUserHolder;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 审计字段自动填充。
 *
 * <p>插入时填 {@code createdAt} / {@code updatedAt} / {@code createdBy}，
 * 更新时填 {@code updatedAt}。字段上必须标 {@code @TableField(fill = ...)} 才生效。
 *
 * <p>{@code createdBy} 取自 {@link CurrentUserHolder}——**没有登录上下文时留 null**
 * （系统任务、定时任务、异步消费等场景），不做兜底猜测。
 *
 * <p>时间统一写 UTC：{@link LocalDateTime} 不带时区信息，若存本地时间则跨时区部署后
 * 数据无法比较。由 API 层负责带上时区偏移再返回给前端。
 */
@Component
public class AuditMetaObjectHandler implements MetaObjectHandler {

    private static final String CREATED_AT = "createdAt";
    private static final String UPDATED_AT = "updatedAt";
    private static final String CREATED_BY = "createdBy";

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        strictInsertFill(metaObject, CREATED_AT, LocalDateTime.class, now);
        strictInsertFill(metaObject, UPDATED_AT, LocalDateTime.class, now);

        Long userId = CurrentUserHolder.currentUserId();
        if (userId != null) {
            strictInsertFill(metaObject, CREATED_BY, Long.class, userId);
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        // 这里不能用 strictUpdateFill：它只在字段为 null 时填充，而从数据库读出的实体
        // updatedAt 本来就非空——那样 updatedAt 永远不会被刷新。
        if (metaObject != null && metaObject.hasSetter(UPDATED_AT)) {
            metaObject.setValue(UPDATED_AT, LocalDateTime.now(ZoneOffset.UTC));
        }
    }
}
