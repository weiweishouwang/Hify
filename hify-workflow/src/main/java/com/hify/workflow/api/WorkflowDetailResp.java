package com.hify.workflow.api;

/**
 * 工作流详情。
 *
 * <p>除了摘要里的字段，还带<b>定义原文</b>（用户手写的那段 JSON 文本）——
 * 编辑器要原样回填，不能替用户重新格式化。
 */
public class WorkflowDetailResp extends WorkflowSummaryResp {

    /** 定义原文（JSON 文本）。 */
    private String definition;

    public String getDefinition() {
        return definition;
    }

    public void setDefinition(String definition) {
        this.definition = definition;
    }
}
