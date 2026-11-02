package com.hify.workflow.api;

/**
 * 运行工作流的请求。
 *
 * <p>入参是 JSON 对象；工作流里用 {@code {{input.<字段名>}}} 引用。
 */
public class WorkflowRunReq {

    /** 入参的 JSON 文本；为空表示没有入参。 */
    private String input;

    public String getInput() {
        return input;
    }

    public void setInput(String input) {
        this.input = input;
    }
}
