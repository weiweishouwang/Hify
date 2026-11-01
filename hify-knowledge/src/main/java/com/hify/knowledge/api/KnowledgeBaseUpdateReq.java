package com.hify.knowledge.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 更新知识库的请求。
 *
 * <p><b>分块参数与向量化模型创建后不可改</b> —— 改了会让已写入 {@code vec_chunk}
 * 的分段与新的分块规则对不上，只能重建知识库。
 */
public class KnowledgeBaseUpdateReq {

    /** 名称，全局唯一。 */
    @Size(max = 128, message = "名称最长 128 字符")
    private String name;

    /** 描述。 */
    @Size(max = 512, message = "描述最长 512 字符")
    private String description;

    /** 状态：active / disabled。 */
    @Pattern(regexp = "active|disabled", message = "状态只支持 active 或 disabled")
    private String status;

    /** 知识库配置版本，用于乐观锁；不匹配则拒绝写入。 */
    @NotNull(message = "缺少知识库配置版本")
    @Positive(message = "知识库配置版本必须为正数")
    private Long configVersion;

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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getConfigVersion() {
        return configVersion;
    }

    public void setConfigVersion(Long configVersion) {
        this.configVersion = configVersion;
    }
}
