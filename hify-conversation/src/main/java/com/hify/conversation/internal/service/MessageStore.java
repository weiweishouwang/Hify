package com.hify.conversation.internal.service;

import com.hify.conversation.internal.entity.ConversationDO;
import com.hify.conversation.internal.entity.MessageDO;
import com.hify.conversation.internal.mapper.ConversationMapper;
import com.hify.conversation.internal.mapper.MessageMapper;
import com.hify.shared.json.JsonUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 消息与运行记录的落库。
 *
 * <p><b>每次写都是独立的一次操作</b> —— 写库不能包在「调上游」的慢操作里
 * （CLAUDE.md §3.1 E5）。
 */
@Service
public class MessageStore {

    private final MessageMapper messages;
    private final ConversationMapper conversations;
    private final JsonUtils json;

    public MessageStore(MessageMapper messages, ConversationMapper conversations, JsonUtils json) {
        this.messages = messages;
        this.conversations = conversations;
        this.json = json;
    }

    /** 落一条用户消息。 */
    @Transactional(rollbackFor = Exception.class)
    public Long appendUser(Long sessionId, String content) {
        MessageDO message = new MessageDO();
        message.setSessionId(sessionId);
        message.setRole(ConversationSupport.ROLE_USER);
        message.setContent(content);
        message.setContentType(ConversationSupport.CONTENT_TYPE_TEXT);
        message.setTokenCount(0);
        messages.insert(message);
        return message.getId();
    }

    /**
     * 落一条助手消息。
     *
     * @param tokenCount   本条消息的 token 数
     * @param finishReason 结束原因
     * @param latencyMs    生成耗时
     * @return 消息主键
     */
    @Transactional(rollbackFor = Exception.class)
    public Long appendAssistant(Long sessionId, String content, Integer tokenCount,
                                String finishReason, Integer latencyMs) {
        MessageDO message = new MessageDO();
        message.setSessionId(sessionId);
        message.setRole(ConversationSupport.ROLE_ASSISTANT);
        message.setContent(content);
        message.setContentType(ConversationSupport.CONTENT_TYPE_TEXT);
        message.setTokenCount(tokenCount == null ? 0 : tokenCount);
        message.setFinishReason(finishReason);
        message.setLatencyMs(latencyMs);
        messages.insert(message);
        return message.getId();
    }

    /**
     * 落一条工具结果消息。
     *
     * <p>正文是一段 JSON：调用标识、工具名、工具标识、入参、结果或错误。
     * 出接口时再拆成「结果正文 + 工具调用明细」。
     *
     * @param toolKey 工具标识（来源:工具标识）；回放历史时靠它找回函数名
     * @return 消息主键
     */
    @Transactional(rollbackFor = Exception.class)
    public Long appendTool(Long sessionId, String toolCallId, String name, String toolKey,
                            String arguments, String result, String error) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("toolCallId", toolCallId == null ? "" : toolCallId);
        payload.put("name", name == null ? "" : name);
        payload.put("toolKey", toolKey == null ? "" : toolKey);
        payload.put("arguments", arguments == null ? "{}" : arguments);
        payload.put("result", result == null ? "" : result);
        payload.put("error", error);
        MessageDO message = new MessageDO();
        message.setSessionId(sessionId);
        message.setRole(ConversationSupport.ROLE_TOOL);
        message.setContent(json.toJson(payload));
        message.setContentType(ConversationSupport.CONTENT_TYPE_JSON);
        message.setTokenCount(0);
        messages.insert(message);
        return message.getId();
    }

    /**
     * 会话计数与标题。
     *
     * @param sessionId 会话主键
     * @param title     标题；为空表示不改（用户改过的标题不能被覆盖）
     */
    @Transactional(rollbackFor = Exception.class)
    public void touchSession(Long sessionId, String title) {
        ConversationDO update = new ConversationDO();
        update.setId(sessionId);
        update.setLastMessageAt(LocalDateTime.now(ZoneOffset.UTC));
        update.setMessageCount(countMessages(sessionId));
        if (title != null && !title.isBlank()) {
            update.setTitle(title);
        }
        conversations.updateById(update);
    }

    /** 会话里的消息数。 */
    public int countMessages(Long sessionId) {
        Long count = messages.selectCount(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<MessageDO>()
                .eq("session_id", sessionId));
        return count == null ? 0 : count.intValue();
    }
}
