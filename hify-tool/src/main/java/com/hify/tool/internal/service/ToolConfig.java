package com.hify.tool.internal.service;

/** {@code tool_definition.config} 这个 JSON 列的键名。 */
public final class ToolConfig {

    /** http 来源：请求地址。 */
    public static final String URL = "url";

    /** http 来源：请求方法。 */
    public static final String METHOD = "method";

    /** http 来源：请求头；敏感头的值是加密封套。 */
    public static final String HEADERS = "headers";

    /** http 来源：超时毫秒数。 */
    public static final String TIMEOUT_MS = "timeoutMs";

    /** builtin 来源：实现该工具的 provider bean 名。 */
    public static final String PROVIDER_BEAN = "providerBean";

    /** mcp 来源：所属 MCP server 的对外 ID。 */
    public static final String MCP_SERVER_PUBLIC_ID = "mcpServerPublicId";

    /** mcp 来源：上游的 tool name。 */
    public static final String MCP_TOOL_NAME = "mcpToolName";

    private ToolConfig() {
    }
}
