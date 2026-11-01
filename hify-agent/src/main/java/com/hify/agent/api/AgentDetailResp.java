package com.hify.agent.api;

import java.time.LocalDateTime;
import java.util.List;

/** Agent 详情：含提示词、绑定项与参数区。 */
public class AgentDetailResp {

    /** 内部主键，供模块间关联使用。 */
    private Long id;

    /** 对外 Agent 标识。 */
    private String publicId;

    /** 名称。 */
    private String name;

    /** 描述。 */
    private String description;

    /** 主模型对外标识。 */
    private String modelPublicId;

    /** 主模型展示名，仅用于展示。 */
    private String modelDisplayName;

    /** 系统提示词。 */
    private String promptTemplate;

    /** 绑定的工具对外 ID 列表。 */
    private List<String> toolIds;

    /** 绑定的知识库对外 ID 列表。 */
    private List<String> knowledgeIds;

    /** 参数区。 */
    private AgentParamConfig paramConfig;

    /** 状态：draft / published / disabled。 */
    private String status;

    /** 形态：一期恒为 chat。 */
    private String appType;

    /**
     * 当前不可用的引用数（工具 + 知识库）。
     *
     * <p>绑定的工具或知识库后来被停用 / 删除时，Agent 运行时它们会静默缺席 ——
     * 这个数字就是提醒管理台「有东西掉线了」。
     */
    private Integer unavailableCount;

    /** Agent 配置版本，用于乐观锁。 */
    private Long configVersion;

    /** 创建时间(UTC)。 */
    private LocalDateTime createdAt;

    /** 更新时间(UTC)。 */
    private LocalDateTime updatedAt;

    public String getPublicId() {
        return publicId;
    }

    public void setPublicId(String publicId) {
        this.publicId = publicId;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public String getModelPublicId() {
        return modelPublicId;
    }

    public void setModelPublicId(String modelPublicId) {
        this.modelPublicId = modelPublicId;
    }

    public String getModelDisplayName() {
        return modelDisplayName;
    }

    public void setModelDisplayName(String modelDisplayName) {
        this.modelDisplayName = modelDisplayName;
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

    public AgentParamConfig getParamConfig() {
        return paramConfig;
    }

    public void setParamConfig(AgentParamConfig paramConfig) {
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

    public Integer getUnavailableCount() {
        return unavailableCount;
    }

    public void setUnavailableCount(Integer unavailableCount) {
        this.unavailableCount = unavailableCount;
    }

    public Long getConfigVersion() {
        return configVersion;
    }

    public void setConfigVersion(Long configVersion) {
        this.configVersion = configVersion;
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
}
