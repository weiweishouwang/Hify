package com.hify.conversation.internal.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.hify.agent.api.AgentBundle;
import com.hify.ai.api.ChatMessage;
import com.hify.ai.api.ChatToolCall;
import com.hify.conversation.internal.entity.MessageDO;
import com.hify.conversation.internal.mapper.MessageMapper;
import com.hify.knowledge.api.KnowledgeBaseSummaryResp;
import com.hify.knowledge.api.KnowledgeSearchService;
import com.hify.knowledge.api.SearchQuery;
import com.hify.knowledge.api.SearchResult;
import com.hify.shared.json.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 组装送给模型的上下文。
 *
 * <p>三部分：
 * <ul>
 *   <li><b>系统提示词</b> —— Agent 的提示词 + 知识库检索到的资料</li>
 *   <li><b>历史消息</b> —— 最近若干轮（工具调用与工具结果一起带上）</li>
 *   <li><b>本次输入</b></li>
 * </ul>
 *
 * <p>上下文<b>必须有界</b>（CLAUDE.md §3.5）：历史条数与检索片段的字符数都有上限。
 */
@Component
public class ContextBuilder {

    private static final Logger log = LoggerFactory.getLogger(ContextBuilder.class);

    private final MessageMapper messages;
    private final KnowledgeSearchService search;
    private final JsonUtils json;

    public ContextBuilder(MessageMapper messages, KnowledgeSearchService search, JsonUtils json) {
        this.messages = messages;
        this.search = search;
        this.json = json;
    }

    /**
     * 组装上下文。
     *
     * @param sessionId 会话主键
     * @param bundle    Agent 的组装结果
     * @param userInput 本次用户输入
     * @return 上下文
     */
    public BuiltContext build(Long sessionId, AgentBundle bundle, String userInput) {
        List<Citation> citations = retrieve(bundle, userInput);
        List<ChatMessage> result = new ArrayList<>();
        String system = composeSystem(bundle.promptTemplate(), citations);
        if (!system.isBlank()) {
            result.add(ChatMessage.system(system));
        }
        result.addAll(loadHistory(sessionId, bundle));
        result.add(ChatMessage.user(userInput));
        return new BuiltContext(result, citations);
    }

    /**
     * 知识库检索。
     *
     * <p>每个绑定的知识库各检一次。检索失败不影响对话 —— 拿不到资料就当没有资料，
     * 不能因为一个知识库连不上就整轮失败。
     */
    private List<Citation> retrieve(AgentBundle bundle, String userInput) {
        List<Citation> citations = new ArrayList<>();
        List<KnowledgeBaseSummaryResp> knowledgeBases = bundle.knowledgeBases();
        if (knowledgeBases == null || knowledgeBases.isEmpty()) {
            return citations;
        }
        SearchQuery query = new SearchQuery();
        query.setQuery(userInput);
        query.setTopK(ConversationSupport.RETRIEVE_TOP_K);
        for (KnowledgeBaseSummaryResp kb : knowledgeBases) {
            try {
                for (SearchResult hit : search.search(kb.getPublicId(), query)) {
                    citations.add(new Citation(kb.getId(), hit.chunkId(), hit.docId(), hit.chunkIndex(),
                            hit.docName(), hit.content(), hit.score()));
                }
            } catch (RuntimeException exception) {
                log.warn("知识库检索失败 kb={} reason={}", kb.getPublicId(), exception.getClass().getSimpleName());
            }
        }
        return citations;
    }

    /** 系统提示词 = Agent 提示词 + 检索到的资料。 */
    private String composeSystem(String promptTemplate, List<Citation> citations) {
        StringBuilder system = new StringBuilder();
        if (promptTemplate != null && !promptTemplate.isBlank()) {
            system.append(promptTemplate.trim());
        }
        if (citations.isEmpty()) {
            return system.toString();
        }
        if (!system.isEmpty()) {
            system.append("\n\n");
        }
        system.append("【参考资料】\n");
        int budget = ConversationSupport.MAX_CONTEXT_CHARS;
        for (Citation citation : citations) {
            String block = "【" + (citation.docName() == null ? "" : citation.docName()) + "】"
                    + citation.quoteText() + "\n";
            if (block.length() > budget) {
                block = block.substring(0, budget);
            }
            system.append(block);
            budget -= block.length();
            if (budget <= 0) {
                break;
            }
        }
        return system.toString();
    }

    /**
     * 历史消息。
     *
     * <p>只带最近 {@link ConversationSupport#MAX_HISTORY_MESSAGES} 条。
     * 工具调用消息与它前面的助手消息成对还原：助手消息带上工具调用清单，
     * 工具消息带回灌结果。
     */
    private List<ChatMessage> loadHistory(Long sessionId, AgentBundle bundle) {
        List<MessageDO> rows = messages.selectList(new QueryWrapper<MessageDO>()
                .eq("session_id", sessionId)
                .orderByDesc("created_at", "id")
                .last("LIMIT " + ConversationSupport.MAX_HISTORY_MESSAGES));
        Collections.reverse(rows);

        Map<String, String> functionNames = ToolFunctionNames.byToolKey(bundle);
        List<ChatMessage> result = new ArrayList<>();
        int index = 0;
        while (index < rows.size()) {
            MessageDO row = rows.get(index);
            if (ConversationSupport.ROLE_USER.equals(row.getRole())) {
                result.add(ChatMessage.user(row.getContent()));
                index++;
                continue;
            }
            if (!ConversationSupport.ROLE_ASSISTANT.equals(row.getRole())) {
                index++;
                continue;
            }
            ChatMessage assistant = ChatMessage.assistant(row.getContent());
            List<ChatToolCall> calls = new ArrayList<>();
            List<ChatMessage> toolMessages = new ArrayList<>();
            int cursor = index + 1;
            while (cursor < rows.size() && ConversationSupport.ROLE_TOOL.equals(rows.get(cursor).getRole())) {
                JsonNode tool = parseTool(rows.get(cursor).getContent());
                String toolKey = tool.path("toolKey").asText("");
                String functionName = functionNames.get(toolKey);
                if (functionName != null) {
                    String callId = tool.path("toolCallId").asText("");
                    calls.add(new ChatToolCall(callId, functionName, tool.path("arguments").asText("{}")));
                    toolMessages.add(ChatMessage.tool(callId, functionName, tool.path("result").asText("")));
                }
                cursor++;
            }
            if (!calls.isEmpty()) {
                assistant.setToolCalls(calls);
            }
            result.add(assistant);
            result.addAll(toolMessages);
            index = cursor;
        }
        return result;
    }

    private JsonNode parseTool(String raw) {
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

    /**
     * 组装好的上下文。
     *
     * @param messages  送给模型的消息
     * @param citations 引用到的知识库片段
     */
    public record BuiltContext(List<ChatMessage> messages, List<Citation> citations) {
    }

    /**
     * 一条引用。
     *
     * @param kbId       知识库主键
     * @param chunkId    分段主键
     * @param docId      文档主键
     * @param chunkIndex 分段序号
     * @param docName    文档名
     * @param quoteText  分段正文（快照）
     * @param score      相似度
     */
    public record Citation(Long kbId, Long chunkId, Long docId, Integer chunkIndex,
                           String docName, String quoteText, Double score) {
    }
}
