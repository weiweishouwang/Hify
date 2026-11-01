package com.hify.agent.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 新建 Agent 的请求。 */
public class AgentCreateReq {

    /** 名称，全局唯一。 */
    @NotBlank(message = "名称不能为空")
    @Size(max = 128, message = "名称最长 128 字符")
    private String name;

    /** 描述。 */
    @Size(max = 512, message = "描述最长 512 字符")
    private String description;

    /** 主模型对外标识；必须是已启用的 chat 模型。 */
    @NotBlank(message = "必须选择一个主模型")
    @Size(max = 26, message = "模型标识格式不合法")
    private String modelPublicId;

    /** 系统提示词。 */
    @Size(max = 65535, message = "提示词过长")
    private String promptTemplate;

    /** 绑定的工具对外 ID 列表。 */
    @Size(max = 100, message = "单个 Agent 最多绑 100 个工具")
    private List<@Size(max = 26, message = "工具标识格式不合法") String> toolIds;

    /** 绑定的知识库对外 ID 列表。 */
    @Size(max = 20, message = "单个 Agent 最多绑 20 个知识库")
    private List<@Size(max = 26, message = "知识库标识格式不合法") String> knowledgeIds;

    /** 参数区。 */
    @Valid
    private AgentParamConfig paramConfig;

    /** 状态；不填默认草稿。 */
    @Pattern(regexp = "draft|published|disabled", message = "不支持的 Agent 状态")
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

    public String getModelPublicId() {
        return modelPublicId;
    }

    public void setModelPublicId(String modelPublicId) {
        this.modelPublicId = modelPublicId;
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
}
