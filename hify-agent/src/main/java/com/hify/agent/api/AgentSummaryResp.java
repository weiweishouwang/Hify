package com.hify.agent.api;

import java.time.LocalDateTime;

/** Agent 摘要：列表项用，不含提示词正文与绑定明细。 */
public class AgentSummaryResp {

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

    /** 状态：draft / published / disabled。 */
    private String status;

    /** 绑定的工具数。 */
    private Integer toolCount;

    /** 绑定的知识库数。 */
    private Integer knowledgeCount;

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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getToolCount() {
        return toolCount;
    }

    public void setToolCount(Integer toolCount) {
        this.toolCount = toolCount;
    }

    public Integer getKnowledgeCount() {
        return knowledgeCount;
    }

    public void setKnowledgeCount(Integer knowledgeCount) {
        this.knowledgeCount = knowledgeCount;
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
