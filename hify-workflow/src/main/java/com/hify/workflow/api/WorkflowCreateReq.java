package com.hify.workflow.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 新建工作流的请求。 */
public class WorkflowCreateReq {

    /** 名称，全局唯一。 */
    @NotBlank(message = "名称不能为空")
    @Size(max = 128, message = "名称最长 128 字符")
    private String name;

    /** 描述。 */
    @Size(max = 512, message = "描述最长 512 字符")
    private String description;

    /** 工作流 JSON 的文本形式（节点与连线）。 */
    @NotBlank(message = "工作流定义不能为空")
    @Size(max = 65535, message = "工作流定义过长")
    private String definition;

    /** 状态：draft / published / disabled；不填默认草稿。 */
    @Pattern(regexp = "draft|published|disabled", message = "不支持的工作流状态")
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

    public String getDefinition() {
        return definition;
    }

    public void setDefinition(String definition) {
        this.definition = definition;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
