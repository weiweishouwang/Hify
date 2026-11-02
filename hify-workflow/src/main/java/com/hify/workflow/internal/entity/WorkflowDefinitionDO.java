package com.hify.workflow.internal.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.hify.shared.mybatis.BaseEntity;
import com.hify.workflow.api.WorkflowDefinition;

/**
 * 工作流定义的数据对象。
 *
 * <p>工作流是<b>配置表</b>（有 {@code deleted}、有 {@code created_by}），
 * 所以继承 {@link BaseEntity}。
 *
 * <p>{@code definition} 列存的是解析后的结构（不是用户手写的原文）——
 * JSON 列存不了「原文 + 校验」两件事，原文格式化由服务端统一。
 */
@TableName(value = "wf_definition", autoResultMap = true)
public class WorkflowDefinitionDO extends BaseEntity {

    /** 对外 ID，ULID。 */
    @TableField("public_id")
    private String publicId;

    /** 名称，全局唯一。 */
    @TableField("name")
    private String name;

    /** 描述。 */
    @TableField("description")
    private String description;

    /** 工作流定义：节点与连线。 */
    @TableField(value = "definition", typeHandler = JacksonTypeHandler.class)
    private WorkflowDefinition definition;

    /**
     * 版本号，每次更新加一；<b>用作乐观锁</b>。
     *
     * <p>表上只有这一个版本列，所以它同时承担「这是第几版」与「防止并发覆盖」两件事。
     */
    @TableField("version")
    private Integer version;

    /** 状态：draft / published / disabled。 */
    @TableField("status")
    private String status;

    public String getPublicId() {
        return publicId;
    }

    public void setPublicId(String publicId) {
        this.publicId = publicId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public WorkflowDefinition getDefinition() {
        return definition;
    }

    public void setDefinition(WorkflowDefinition definition) {
        this.definition = definition;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
