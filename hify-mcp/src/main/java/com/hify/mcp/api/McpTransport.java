package com.hify.mcp.api;

/** MCP 服务的传输方式。 */
public enum McpTransport {

    /** stdio：本地拉起一个进程，通过标准输入输出通信。适合本地开发与内部小工具。 */
    STDIO("stdio"),

    /** http：Streamable HTTP 传输。生产环境推荐 —— 副本间不各自 spawn 进程。 */
    HTTP("http");

    private final String code;

    McpTransport(String code) {
        this.code = code;
    }

    /** 存库与传输用的字面量。 */
    public String code() {
        return code;
    }

    /**
     * 按字面量解析传输方式。
     *
     * @param code 字面量
     * @return 对应的枚举
     * @throws com.hify.shared.exception.BizException 未知传输方式时抛出
     */
    public static McpTransport fromCode(String code) {
        for (McpTransport transport : values()) {
            if (transport.code.equals(code)) {
                return transport;
            }
        }
        throw new com.hify.shared.exception.BizException(
                com.hify.shared.exception.ErrorCode.PARAM_ERROR, "不支持的 MCP 传输方式: " + code);
    }
}
