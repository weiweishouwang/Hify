package com.hify.tool.api;

import com.hify.shared.exception.BizException;
import com.hify.shared.exception.ErrorCode;

/** 工具来源。存库的 {@code source_type} 取值与枚举 code 一一对应。 */
public enum ToolSourceType {

    /** 内置工具：代码注册，随应用发布。 */
    BUILTIN("builtin"),

    /** 自定义 HTTP 工具：用户在管理台创建。 */
    HTTP("http"),

    /** MCP 工具：来自 MCP server 的 tools/list 动态发现。 */
    MCP("mcp");

    private final String code;

    ToolSourceType(String code) {
        this.code = code;
    }

    /** 存库与传输用的字面量。 */
    public String code() {
        return code;
    }

    /**
     * 按字面量解析来源。
     *
     * @param code 来源字面量
     * @return 对应的枚举
     * @throws BizException 未知来源时抛出
     */
    public static ToolSourceType fromCode(String code) {
        for (ToolSourceType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new BizException(ErrorCode.PARAM_ERROR, "不支持的工具来源: " + code);
    }
}
