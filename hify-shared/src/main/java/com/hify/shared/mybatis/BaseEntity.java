package com.hify.shared.mybatis;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;

import java.time.LocalDateTime;

/**
 * 业务实体的公共基类：主键、审计字段与逻辑删除。
 *
 * <p>只放**纯技术字段**，业务字段一律留在各模块自己的 DO 里——基类一旦承载业务，
 * 就会变成所有模块的依赖。
 *
 * <p><b>事件流表不要继承本类</b>：事件流表（{@code conv_message} / {@code conv_run} /
 * {@code conv_message_citation} / {@code wf_run}）是分区表，主键是 {@code (id, created_at)}
 * 复合主键，且分区表的唯一键必须包含分区列，因此无法使用本类的单列主键。
 *
 * <p><b>软删字段用 {@code deleted}（0/1）而不是 {@code deleted_at}</b>：{@code @TableLogic}
 * 会给每条 SQL 追加 {@code deleted = 0} 条件，分区表继承本类就会破坏分区裁剪——所以
 * 事件流表一律不继承。用状态机表达状态（软删/索引中/失败等）的表也不该再叠一层软删。
 *
 * <p>时间字段统一存 UTC（见 CLAUDE.md §3.3），由
 * {@link AuditMetaObjectHandler} 自动填充。
 */
public class BaseEntity {

    /** 主键，由数据库自增生成。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 创建时间（UTC），插入时自动填充。 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 最后更新时间（UTC），插入与更新时自动填充。 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /**
     * 逻辑删除标记：0 未删除 / 1 已删除。
     *
     * <p>初值给 0 是为了让「插入时这一列写什么」是显式的、可预期的，而不是依赖
     * 建表语句里的 {@code DEFAULT 0}——换一套 DDL 时行为不会悄悄变。
     *
     * <p>（MyBatis-Plus 插入时只在值非空才写这一列，值为 null 就整列跳过、
     * 交给库里的默认值，所以两种写法都不会写出 NULL。）
     *
     * <p>{@code @TableLogic} 让 MyBatis-Plus 自动做三件事：查询追加 {@code deleted = 0}、
     * {@code deleteById} 改写成 {@code UPDATE SET deleted = 1}、更新语句不碰这一列。
     */
    @TableLogic
    private Integer deleted = 0;

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

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }
}
