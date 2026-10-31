package com.hify.tool.api;

import java.time.LocalDateTime;

/** 工具摘要：不含入参 schema，也不含请求头。 */
public class ToolSummaryResp {

    /** 内部主键，供模块间关联使用。 */
    private Long id;

    /** 对外工具标识。 */
    private String publicId;

    /** 工具名，进 function calling 的 function name。 */
    private String name;

    /** 来源：builtin / http / mcp。 */
    private String sourceType;

    /** 来源内唯一的工具标识。 */
    private String toolKey;

    /** 工具描述，给模型看的。 */
    private String description;

    /** 是否启用。 */
    private Boolean enabled;

    /** 工具配置版本，用于乐观锁。 */
    private Long configVersion;

    /** 创建时间(UTC)。 */
    private LocalDateTime createdAt;

    /** 更新时间(UTC)。 */
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getToolKey() {
        return toolKey;
    }

    public void setToolKey(String toolKey) {
        this.toolKey = toolKey;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
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
