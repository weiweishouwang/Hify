package com.hify.conversation.internal.service;

import com.hify.shared.exception.BizException;
import com.hify.shared.exception.ErrorCode;

/** 会话与对话的常量、限制与校验。 */
public final class ConversationSupport {

    /** 会话状态：可用。 */
    public static final String STATUS_ACTIVE = "active";

    /** 会话状态：已归档。 */
    public static final String STATUS_ARCHIVED = "archived";

    /** 消息角色：用户。 */
    public static final String ROLE_USER = "user";

    /** 消息角色：模型。 */
    public static final String ROLE_ASSISTANT = "assistant";

    /** 消息角色：工具结果。 */
    public static final String ROLE_TOOL = "tool";

    /** 正文类型：纯文本。 */
    public static final String CONTENT_TYPE_TEXT = "text";

    /** 正文类型：JSON（工具调用消息）。 */
    public static final String CONTENT_TYPE_JSON = "json";

    /** 运行状态：进行中。 */
    public static final String RUN_RUNNING = "running";

    /** 运行状态：成功。 */
    public static final String RUN_SUCCEEDED = "succeeded";

    /** 运行状态：失败。 */
    public static final String RUN_FAILED = "failed";

    /**
     * 工具调用最多几轮。
     *
     * <p>防止模型反复要同一个工具把额度烧光。超限就以「达到工具调用轮次上限」收尾，
     * 已经拿到的内容照样交给用户。
     */
    public static final int MAX_TOOL_ROUNDS = 5;

    /** 送给模型的历史消息条数上限。 */
    public static final int MAX_HISTORY_MESSAGES = 20;

    /** 检索片段注入上下文的字符预算。 */
    public static final int MAX_CONTEXT_CHARS = 4000;

    /** 单条用户输入的字符上限。 */
    public static final int MAX_MESSAGE_CHARS = 4000;

    /** 每个知识库检索几条。 */
    public static final int RETRIEVE_TOP_K = 3;

    /** 消息历史默认返回条数。 */
    public static final int DEFAULT_MESSAGE_LIMIT = 50;

    /** 消息历史上限。 */
    public static final int MAX_MESSAGE_LIMIT = 200;

    /** 会话标题取首条用户消息的前若干字。 */
    public static final int TITLE_LENGTH = 30;

    private ConversationSupport() {
    }

    /** 参数错误。 */
    public static BizException invalid(String message) {
        return new BizException(ErrorCode.PARAM_ERROR, message);
    }

    /** 会话状态：active / archived。 */
    public static String status(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String status = value.trim();
        if (!STATUS_ACTIVE.equals(status) && !STATUS_ARCHIVED.equals(status)) {
            throw invalid("会话状态只支持 active 或 archived");
        }
        return status;
    }

    /** 会话标题。 */
    public static String title(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() > 255) {
            throw invalid("标题最长 255 字符");
        }
        return trimmed;
    }

    /** 用户输入。 */
    public static String content(String value) {
        if (value == null || value.isBlank()) {
            throw invalid("消息内容不能为空");
        }
        String trimmed = value.trim();
        if (trimmed.length() > MAX_MESSAGE_CHARS) {
            throw invalid("单条消息最长 " + MAX_MESSAGE_CHARS + " 字符");
        }
        return trimmed;
    }

    /** 消息历史条数。 */
    public static int messageLimit(Integer value) {
        if (value == null) {
            return DEFAULT_MESSAGE_LIMIT;
        }
        return Math.max(1, Math.min(value, MAX_MESSAGE_LIMIT));
    }

    /** 从首条用户消息生成标题。 */
    public static String deriveTitle(String content) {
        if (content == null || content.isBlank()) {
            return "新会话";
        }
        String trimmed = content.trim().replaceAll("\\s+", " ");
        return trimmed.length() <= TITLE_LENGTH ? trimmed : trimmed.substring(0, TITLE_LENGTH) + "…";
    }
}
