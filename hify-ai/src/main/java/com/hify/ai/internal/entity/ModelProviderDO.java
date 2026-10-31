package com.hify.ai.internal.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.hify.shared.mybatis.BaseEntity;

import java.util.Map;

/**
 * 模型接入点的数据对象：一个协议、一个端点和一套鉴权配置。
 *
 * <p>健康状态独立存储，不随配置缓存；实体只在本模块 internal 内使用。
 */
@TableName(value = "ai_model_provider", autoResultMap = true)
public class ModelProviderDO extends BaseEntity {

    /** 对外 ID，ULID（Universally Unique Lexicographically Sortable Identifier）。 */
    @TableField("public_id")
    private String publicId;

    /** 接入点名称，同一厂商的不同账号可以分别配置。 */
    @TableField("name")
    private String name;

    /** 协议类型：openai / anthropic / ollama。 */
    @TableField("type")
    private String type;

    /** 协议根地址，具体请求路径由适配器追加。 */
    @TableField("base_url")
    private String baseUrl;

    /** 按协议存储的鉴权配置；敏感值由业务层加密后写入，TypeHandler 只负责 JSON 转换。 */
    @TableField(value = "auth_config", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> authConfig;

    /** 接入点级的上游扩展参数，不存放凭据。 */
    @TableField(value = "extra_params", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> extraParams;

    /** 是否允许使用此接入点。 */
    @TableField("enabled")
    private Boolean enabled;

    /** 连接配置版本，用于识别旧配置产生的健康观测；由业务层递增。 */
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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public Map<String, Object> getAuthConfig() {
        return authConfig;
    }

    public void setAuthConfig(Map<String, Object> authConfig) {
        this.authConfig = authConfig;
    }

    public Map<String, Object> getExtraParams() {
        return extraParams;
    }

    public void setExtraParams(Map<String, Object> extraParams) {
        this.extraParams = extraParams;
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
