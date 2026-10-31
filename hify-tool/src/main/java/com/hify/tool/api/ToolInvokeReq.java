package com.hify.tool.api;

import java.util.Map;

/** 手工测试一个工具的请求。 */
public class ToolInvokeReq {

    /** 模型给出的入参；整体作为 HTTP 请求体（POST）或查询参数（GET）。 */
    private Map<String, Object> args;

    public Map<String, Object> getArgs() {
        return args;
    }

    public void setArgs(Map<String, Object> args) {
        this.args = args;
    }
}
