package com.hify.agent.api;

import com.hify.shared.exception.BizException;
import com.hify.shared.exception.ErrorCode;

/**
 * Agent 的状态。
 *
 * <p>一期不做发布快照（{@code ai_agent_version}），所以「发布」只是把状态翻成
 * {@link #PUBLISHED}，<b>没有版本概念</b> —— 改完配置直接生效。
 *
 * <p>编排层（对话、工作流）只接受 {@link #PUBLISHED} 的 Agent。
 */
public enum AgentStatus {

    /** 草稿：还在配，编排层不认。 */
    DRAFT("draft", "草稿"),

    /** 已发布：可用。 */
    PUBLISHED("published", "已发布"),

    /** 已停用：曾经可用，现被关掉。 */
    DISABLED("disabled", "已停用");

    private final String code;

    private final String label;

    AgentStatus(String code, String label) {
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
     * 按字面量解析状态。
     *
     * @param code 状态字面量
     * @return 对应的枚举
     * @throws BizException 未知状态时抛出
     */
    public static AgentStatus fromCode(String code) {
        for (AgentStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new BizException(ErrorCode.PARAM_ERROR, "不支持的 Agent 状态: " + code);
    }
}
