package com.hify.tool.internal.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.hify.shared.mybatis.BaseEntity;

import java.util.Map;

/**
 * 工具定义的数据对象。
 *
 * <p>统一目录：内置、HTTP、MCP 三种来源各占一行，靠 {@code sourceType} 区分。
 * 内置与 MCP 的行由同步机制托管，用户只手工创建 http 来源。
 *
 * <p>{@code config} 里的敏感值（http 请求头中的 token 这类）由业务层 AES-GCM 加密后写入，
 * TypeHandler 只负责 JSON 转换、不负责加密。
 */
@TableName(value = "tool_definition", autoResultMap = true)
public class ToolDefinitionDO extends BaseEntity {

    /** 对外 ID，ULID（Universally Unique Lexicographically Sortable Identifier）。 */
    @TableField("public_id")
    private String publicId;

    /** 来源：builtin / http / mcp。 */
    @TableField("source_type")
    private String sourceType;

    /** 来源内唯一的工具标识。 */
    @TableField("tool_key")
    private String toolKey;

    /** 工具名，进 function calling 的 function name。 */
    @TableField("name")
    private String name;

    /** 工具描述，给模型看。 */
    @TableField("description")
    private String description;

    /**
     * 入参 JSON Schema，给模型看；不参与执行期的参数映射。
     *
     * <p>列名是 {@code input_schema} 而不是 {@code schema} —— 后者是 MySQL 8 的保留字，
     * MyBatis-Plus 生成的 SQL 不加反引号，直接语法错误。
     */
    @TableField(value = "input_schema", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> schema;

    /** 来源相关配置，按 sourceType 分化；敏感值已加密。 */
    @TableField(value = "config", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> config;

    /** 是否可用。 */
    @TableField("enabled")
    private Boolean enabled;

    /** 工具配置版本，用于乐观锁；由业务层递增。 */
    @TableField("config_version")
    private Long configVersion;

    public String getPublicId() {
        return publicId;
    }

    public void setPublicId(String publicId) {
        this.publicId = publicId;
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

    public Map<String, Object> getSchema() {
        return schema;
    }

    public void setSchema(Map<String, Object> schema) {
        this.schema = schema;
    }

    public Map<String, Object> getConfig() {
        return config;
    }

    public void setConfig(Map<String, Object> config) {
        this.config = config;
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
}
