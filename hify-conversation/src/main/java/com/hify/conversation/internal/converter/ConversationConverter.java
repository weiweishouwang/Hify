package com.hify.conversation.internal.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.hify.conversation.api.ConversationMessageResp;
import com.hify.conversation.api.ConversationSummaryResp;
import com.hify.conversation.internal.entity.ConversationDO;
import com.hify.conversation.internal.entity.MessageDO;
import com.hify.conversation.internal.service.ConversationSupport;
import com.hify.shared.json.JsonUtils;
import org.springframework.stereotype.Component;

import java.util.List;

/** 会话与消息的数据对象 → 对外 DTO。转换逻辑不堆在 service 里。 */
@Component
public class ConversationConverter {

    private final JsonUtils json;

    public ConversationConverter(JsonUtils json) {
        this.json = json;
    }

    /** 会话摘要。 */
    public ConversationSummaryResp toSummary(ConversationDO row, String agentPublicId, String agentName) {
        ConversationSummaryResp resp = new ConversationSummaryResp();
        resp.setPublicId(row.getPublicId());
        resp.setTitle(row.getTitle());
        resp.setAgentPublicId(agentPublicId);
        resp.setAgentName(agentName);
        resp.setMessageCount(row.getMessageCount());
        resp.setLastMessageAt(row.getLastMessageAt());
        resp.setStatus(row.getStatus());
        resp.setCreatedAt(row.getCreatedAt());
        resp.setUpdatedAt(row.getUpdatedAt());
        return resp;
    }

    /**
     * 消息。
     *
     * <p>工具消息的正文在库里是结构化 JSON，这里拆成「结果正文 + 工具调用明细」两半：
     * 前端展示结果，后者让界面画出工具卡片。
     */
    public ConversationMessageResp toMessage(MessageDO row) {
        ConversationMessageResp resp = new ConversationMessageResp();
        resp.setId(row.getId());
        resp.setRole(row.getRole());
        resp.setContentType(row.getContentType());
        resp.setFinishReason(row.getFinishReason());
        resp.setTokenCount(row.getTokenCount());
        resp.setLatencyMs(row.getLatencyMs());
        resp.setCreatedAt(row.getCreatedAt());
        if (ConversationSupport.ROLE_TOOL.equals(row.getRole())) {
            resp.setContent(toolResult(row.getContent()));
            resp.setToolCalls(List.of(toToolCallView(row.getContent())));
        } else {
            resp.setContent(row.getContent());
        }
        return resp;
    }

    /** 工具消息 → 工具调用明细。 */
    public ConversationMessageResp.ToolCallView toToolCallView(String raw) {
        JsonNode node = parse(raw);
        return new ConversationMessageResp.ToolCallView(
                node.path("toolCallId").asText(""),
                node.path("name").asText(""),
                node.path("arguments").asText(""),
                node.path("result").asText(""),
                node.hasNonNull("error") ? node.path("error").asText("") : null);
    }

    /** 工具消息 → 结果正文。 */
    public String toolResult(String raw) {
        return parse(raw).path("result").asText("");
    }

    private JsonNode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return json.mapper().createObjectNode();
        }
        try {
            JsonNode node = json.mapper().readTree(raw);
            return node == null || !node.isObject() ? json.mapper().createObjectNode() : node;
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            return json.mapper().createObjectNode();
        }
    }
}
