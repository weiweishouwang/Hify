package com.hify.workflow.api;

import com.hify.shared.exception.BizException;
import com.hify.shared.exception.ErrorCode;

/**
 * 工作流的六种节点。
 *
 * <p>CLAUDE.md §1.2 定的范围：LLM / Agent / 工具 / 知识检索 / If-Else / 输出。
 * 没有可视化画布 —— 工作流就是一段 JSON。
 */
public enum WorkflowNodeType {

    /** 模型调用：拼提示词、调模型、拿文本。 */
    LLM("llm", "模型调用"),

    /** 复用已配置好的 Agent：拿它的模型与提示词做一次调用。 */
    AGENT("agent", "Agent"),

    /** 执行一个工具。 */
    TOOL("tool", "工具"),

    /** 知识库检索。 */
    KNOWLEDGE("knowledge", "知识检索"),

    /** 条件分支。 */
    IF("if", "条件分支"),

    /** 产出最终结果。 */
    OUTPUT("output", "输出");

    private final String code;
    private final String label;

    WorkflowNodeType(String code, String label) {
        this.code = code;
        this.label = label;
    }

    /** 存库与传输用的字面量。 */
    public String code() {
        return code;
    }

    /** 管理台展示用的文案。 */
    public String label() {
        return label;
    }

    /**
     * 按字面量解析节点类型。
     *
     * @param code 节点类型字面量
     * @return 对应的枚举
     * @throws BizException 未知类型时抛出
     */
    public static WorkflowNodeType fromCode(String code) {
        for (WorkflowNodeType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new BizException(ErrorCode.PARAM_ERROR, "不支持的节点类型: " + code);
    }
}
