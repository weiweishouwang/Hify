package com.hify.knowledge.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 创建知识库的请求。
 *
 * <p>{@code embeddingModel} 存 {@code ai_model.public_id} —— 不能存上游模型名，
 * 上游模型名跨接入点会重名，无法唯一定位到该往哪个 provider 发请求。
 */
public class KnowledgeBaseCreateReq {

    /** 名称，全局唯一。 */
    @NotBlank(message = "名称不能为空")
    @Size(max = 128, message = "名称最长 128 字符")
    private String name;

    /** 描述。 */
    @Size(max = 512, message = "描述最长 512 字符")
    private String description;

    /** 向量化用的模型（ai_model.public_id），必须是启用状态的 embedding 模型。 */
    @NotBlank(message = "请选择向量化模型")
    @Size(max = 26, message = "模型标识过长")
    private String embeddingModel;

    /** 分块大小，单位字符。 */
    @Min(value = 100, message = "分块大小不能小于 100 字符")
    @Max(value = 4000, message = "分块大小不能大于 4000 字符")
    private Integer chunkSize;

    /** 分块重叠，单位字符。 */
    @Min(value = 0, message = "分块重叠不能为负数")
    @Max(value = 2000, message = "分块重叠不能大于 2000 字符")
    private Integer chunkOverlap;

    /** 状态：active / disabled。 */
    @Pattern(regexp = "active|disabled", message = "状态只支持 active 或 disabled")
    private String status;

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

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public Integer getChunkSize() {
        return chunkSize;
    }

    public void setChunkSize(Integer chunkSize) {
        this.chunkSize = chunkSize;
    }

    public Integer getChunkOverlap() {
        return chunkOverlap;
    }

    public void setChunkOverlap(Integer chunkOverlap) {
        this.chunkOverlap = chunkOverlap;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
