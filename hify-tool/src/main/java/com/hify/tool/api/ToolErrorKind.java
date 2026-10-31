package com.hify.tool.api;

/** 工具执行失败的分类。 */
public enum ToolErrorKind {

    /** 上游超时。 */
    TIMEOUT,

    /** 上游鉴权失败：401 / 403，或凭据缺失。 */
    AUTH_FAILED,

    /** 上游返回错误状态或连接失败。 */
    UPSTREAM_ERROR,

    /** 工具自身执行失败：入参不对、返回格式不对等。 */
    TOOL_ERROR
}
