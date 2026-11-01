package com.hify.agent.internal.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.hify.shared.mybatis.BaseEntity;

import java.util.List;
import java.util.Map;

/**
 * Agent 配置的数据对象。
 *
 * <p>{@code toolIds} / {@code knowledgeIds} 存的是<b>对外 ID（ULID 字符串）</b>，
 * 不是内部主键 —— 公开 ID 跨模块稳定，主键重排不会把引用带断；
 * 表上的多值索引 {@code CAST(tool_ids->'$[*]' AS CHAR(64) ARRAY)} 也是为字符串准备的。
 *
 * <p>{@code modelId} 存的是内部主键 {@code ai_model.id}：模型在同一个库里，
 * 且 {@code ai_agent} 上有 {@code idx_model_id} 二级索引，按主键关联最省事。
 * 对外的模型标识由业务层通过 {@code ai} 模块的 api 解析，不直接暴露给前端。
 */
@TableName(value = "ai_agent", autoResultMap = true)
public class AgentDO extends BaseEntity {

    /** 对外 ID，ULID（Universally Unique Lexicographically Sortable Identifier）。 */
    @TableField("public_id")
    private String publicId;

    /** 名称，全局唯一（含软删行）。 */
    @TableField("name")
    private String name;

    /** 描述。 */
    @TableField("description")
    private String description;

    /** 主模型 ai_model.id。 */
    @TableField("model_id")
    private Long modelId;

    /** 系统提示词。 */
    @TableField("prompt_template")
    private String promptTemplate;

    /** 绑定的工具对外 ID 数组。 */
    @TableField(value = "tool_ids", typeHandler = JacksonTypeHandler.class)
    private List<String> toolIds;

    /** 绑定的知识库对外 ID 数组。 */
    @TableField(value = "knowledge_ids", typeHandler = JacksonTypeHandler.class)
    private List<String> knowledgeIds;

    /** 参数区：temperature / max_tokens / top_p。 */
    @TableField(value = "param_config", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> paramConfig;

    /** 状态：draft / published / disabled。 */
    @TableField("status")
    private String status;

    /** 形态：一期恒为 chat。 */
    @TableField("app_type")
    private String appType;

    /** Agent 配置版本，用于乐观锁；由业务层递增。 */
    @TableField("config_version")
    private Long configVersion;

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

    public Long getModelId() {
        return modelId;
    }

    public void setModelId(Long modelId) {
        this.modelId = modelId;
    }

    public String getPromptTemplate() {
        return promptTemplate;
    }

    public void setPromptTemplate(String promptTemplate) {
        this.promptTemplate = promptTemplate;
    }

    public List<String> getToolIds() {
        return toolIds;
    }

    public void setToolIds(List<String> toolIds) {
        this.toolIds = toolIds;
    }

    public List<String> getKnowledgeIds() {
        return knowledgeIds;
    }

    public void setKnowledgeIds(List<String> knowledgeIds) {
        this.knowledgeIds = knowledgeIds;
    }

    public Map<String, Object> getParamConfig() {
        return paramConfig;
    }

    public void setParamConfig(Map<String, Object> paramConfig) {
        this.paramConfig = paramConfig;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getAppType() {
        return appType;
    }

    public void setAppType(String appType) {
        this.appType = appType;
    }

    public Long getConfigVersion() {
        return configVersion;
    }

    public void setConfigVersion(Long configVersion) {
        this.configVersion = configVersion;
    }
}
