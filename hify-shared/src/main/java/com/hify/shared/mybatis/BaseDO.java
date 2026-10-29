package com.hify.shared.mybatis;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;

import java.time.LocalDateTime;

/**
 * 数据对象的公共基类：主键与审计字段。
 *
 * <p>只放**纯技术字段**，业务字段一律留在各模块自己的 DO 里——基类一旦承载业务，
 * 就会变成所有模块的依赖。
 *
 * <p><b>事件流表不要继承本类</b>：事件流表（{@code conv_message} / {@code conv_run} /
 * {@code conv_message_citation} / {@code wf_run}）是分区表，主键是 {@code (id, created_at)}
 * 复合主键，且分区表的唯一键必须包含分区列，因此无法直接使用本类的单列主键。
 *
 * <p><b>软删字段也不在本类</b>：{@code @TableLogic} 会给每条 SQL 追加
 * {@code deleted_at IS NULL}，破坏分区表的分区裁剪；用状态机表达状态（软删/索引中/失败等）
 * 的表也不该再叠一层软删。需要软删的表自己声明 {@code deletedAt}。
 *
 * <p>时间字段统一存 UTC（见 CLAUDE.md §3.3），由
 * {@link AuditMetaObjectHandler} 自动填充。
 */
public class BaseDO {

    /** 主键，由数据库自增生成。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 创建时间（UTC），插入时自动填充。 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 最后更新时间（UTC），插入与更新时自动填充。 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 创建者用户 id，插入时自动填充；系统任务等无登录上下文的场景为 null。 */
    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }
}
