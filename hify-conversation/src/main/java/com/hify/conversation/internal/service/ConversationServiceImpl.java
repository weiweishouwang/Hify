package com.hify.conversation.internal.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hify.agent.api.AgentDetailResp;
import com.hify.agent.api.AgentService;
import com.hify.conversation.api.ConversationCreateReq;
import com.hify.conversation.api.ConversationListQuery;
import com.hify.conversation.api.ConversationRunListResp;
import com.hify.conversation.api.ConversationRunQuery;
import com.hify.conversation.api.ConversationRunSummaryResp;
import com.hify.conversation.api.ConversationListResp;
import com.hify.conversation.api.ConversationMessageResp;
import com.hify.conversation.api.ConversationService;
import com.hify.conversation.api.ConversationSummaryResp;
import com.hify.conversation.api.ConversationUpdateReq;
import com.hify.conversation.internal.converter.ConversationConverter;
import com.hify.conversation.internal.entity.ConversationDO;
import com.hify.conversation.internal.entity.MessageDO;
import com.hify.conversation.internal.entity.RunDO;
import com.hify.conversation.internal.mapper.ConversationMapper;
import com.hify.conversation.internal.mapper.MessageMapper;
import com.hify.conversation.internal.mapper.RunMapper;
import com.hify.shared.api.CurrentUserService;
import com.hify.shared.exception.BizException;
import com.hify.shared.exception.ErrorCode;
import com.hify.shared.id.IdGenerator;
import com.hify.shared.mybatis.CursorCodec;
import com.hify.shared.web.PageQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 会话的增删改查与消息历史。
 *
 * <p><b>会话归属当前用户</b>：一期没有登录接口，用户由 {@link CurrentUserService} 解析
 * （登录态优先，没有就落到内置的开发用户）。
 */
@Service
public class ConversationServiceImpl implements ConversationService {

    private final ConversationMapper conversations;
    private final MessageMapper messages;
    private final RunMapper runs;
    private final ConversationLookup lookup;
    private final CurrentUserService currentUsers;
    private final AgentService agents;
    private final ConversationConverter converter;

    public ConversationServiceImpl(ConversationMapper conversations, MessageMapper messages, RunMapper runs,
                                   ConversationLookup lookup, CurrentUserService currentUsers,
                                   AgentService agents, ConversationConverter converter) {
        this.conversations = conversations;
        this.messages = messages;
        this.runs = runs;
        this.lookup = lookup;
        this.currentUsers = currentUsers;
        this.agents = agents;
        this.converter = converter;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ConversationSummaryResp create(ConversationCreateReq request) {
        if (request == null) {
            throw ConversationSupport.invalid("会话配置不能为空");
        }
        AgentDetailResp agent = agents.get(request.getAgentPublicId().trim());
        ConversationDO entity = new ConversationDO();
        entity.setPublicId(IdGenerator.nextUlid());
        entity.setUserId(currentUsers.currentUserId());
        entity.setAgentId(agent.getId());
        entity.setTitle(ConversationSupport.title(request.getTitle()));
        entity.setMessageCount(0);
        entity.setStatus(ConversationSupport.STATUS_ACTIVE);
        conversations.insert(entity);
        return converter.toSummary(entity, agent.getPublicId(), agent.getName());
    }

    @Override
    public ConversationListResp list(ConversationListQuery query) {
        ConversationListQuery effective = query == null ? new ConversationListQuery() : query;
        var wrapper = new QueryWrapper<ConversationDO>();
        wrapper.eq("user_id", currentUsers.currentUserId());
        if (effective.getStatus() != null && !effective.getStatus().isBlank()) {
            wrapper.eq("status", effective.getStatus().trim());
        }
        String keyword = effective.getKeyword() == null ? null : effective.getKeyword().trim();
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like("title", keyword);
        }
        if (effective.getCursor() != null && !effective.getCursor().isBlank()) {
            var cursor = CursorCodec.decode(effective.getCursor());
            wrapper.apply("(created_at, id) < ({0}, {1})", cursor.createdAt(), cursor.id());
        }
        int size = size(effective);
        wrapper.orderByDesc("created_at", "id").last("LIMIT " + (size + 1));

        List<ConversationDO> rows = conversations.selectList(wrapper);
        boolean hasNext = rows.size() > size;
        List<ConversationDO> visible = rows.subList(0, Math.min(rows.size(), size));

        List<ConversationSummaryResp> items = new ArrayList<>(visible.size());
        for (ConversationDO row : visible) {
            items.add(converter.toSummary(row, null, null));
        }
        fillAgents(items, visible);

        ConversationListResp result = new ConversationListResp();
        result.setItems(items);
        result.setHasNext(hasNext);
        if (hasNext) {
            ConversationDO last = visible.getLast();
            result.setNextCursor(CursorCodec.encode(last.getCreatedAt(), last.getId()));
        }
        return result;
    }

    @Override
    public ConversationRunListResp listRuns(ConversationRunQuery query) {
        ConversationRunQuery effective = query == null ? new ConversationRunQuery() : query;
        Long userId = currentUsers.currentUserId();
        var wrapper = new QueryWrapper<RunDO>();
        // 只看当前用户的会话。运行记录本身没有 user_id，归属靠会话。
        wrapper.apply("session_id IN (SELECT id FROM conv_session WHERE user_id = {0} AND deleted = 0)", userId);
        if (effective.getStatus() != null && !effective.getStatus().isBlank()) {
            wrapper.eq("status", effective.getStatus().trim());
        }
        if (effective.getCursor() != null && !effective.getCursor().isBlank()) {
            var cursor = CursorCodec.decode(effective.getCursor());
            wrapper.apply("(created_at, id) < ({0}, {1})", cursor.createdAt(), cursor.id());
        }
        int size = size(effective);
        wrapper.orderByDesc("created_at", "id").last("LIMIT " + (size + 1));

        List<RunDO> rows = runs.selectList(wrapper);
        boolean hasNext = rows.size() > size;
        List<RunDO> visible = rows.subList(0, Math.min(rows.size(), size));

        // 会话标题与 Agent 名字：会话批量取，Agent 按 id 去重后逐个取
        Map<Long, ConversationDO> sessions = sessionsById(visible);
        Map<Long, String> agentNames = agentNames(sessions.values());

        List<ConversationRunSummaryResp> items = new ArrayList<>(visible.size());
        for (RunDO row : visible) {
            items.add(toRunSummary(row, sessions.get(row.getSessionId()), agentNames));
        }

        ConversationRunListResp result = new ConversationRunListResp();
        result.setItems(items);
        result.setHasNext(hasNext);
        if (hasNext) {
            RunDO last = visible.getLast();
            result.setNextCursor(CursorCodec.encode(last.getCreatedAt(), last.getId()));
        }
        return result;
    }

    /** 批量取这些运行记录所属的会话。 */
    private Map<Long, ConversationDO> sessionsById(List<RunDO> rows) {
        Set<Long> ids = new LinkedHashSet<>();
        for (RunDO row : rows) {
            if (row.getSessionId() != null) {
                ids.add(row.getSessionId());
            }
        }
        Map<Long, ConversationDO> result = new HashMap<>();
        if (ids.isEmpty()) {
            return result;
        }
        for (ConversationDO session : conversations.selectBatchIds(ids)) {
            result.put(session.getId(), session);
        }
        return result;
    }

    /** Agent 名字：按 id 去重后逐个取 —— 一页里通常只有一两个 Agent。 */
    private Map<Long, String> agentNames(Collection<ConversationDO> sessions) {
        Map<Long, String> names = new HashMap<>();
        for (ConversationDO session : sessions) {
            if (session.getAgentId() == null || names.containsKey(session.getAgentId())) {
                continue;
            }
            try {
                AgentDetailResp agent = agents.getById(session.getAgentId());
                names.put(session.getAgentId(), agent.getName());
            } catch (BizException exception) {
                // Agent 被删了：运行记录还在，只是展示不出名字
                names.put(session.getAgentId(), null);
            }
        }
        return names;
    }

    /** 运行记录 → 摘要。 */
    private ConversationRunSummaryResp toRunSummary(RunDO row, ConversationDO session,
                                                    Map<Long, String> agentNames) {
        ConversationRunSummaryResp resp = new ConversationRunSummaryResp();
        resp.setId(row.getId());
        resp.setStatus(row.getStatus());
        resp.setPromptTokens(row.getPromptTokens());
        resp.setCompletionTokens(row.getCompletionTokens());
        resp.setToolCallCount(row.getToolCallCount());
        resp.setLatencyMs(row.getLatencyMs());
        resp.setErrorMessage(row.getErrorMessage());
        resp.setCreatedAt(row.getCreatedAt());
        if (session != null) {
            resp.setSessionPublicId(session.getPublicId());
            resp.setSessionTitle(session.getTitle());
            if (session.getAgentId() != null) {
                resp.setAgentName(agentNames.get(session.getAgentId()));
            }
        }
        return resp;
    }

    @Override
    public ConversationSummaryResp get(String publicId) {
        ConversationDO row = lookup.requireOwned(publicId);
        ConversationSummaryResp resp = converter.toSummary(row, null, null);
        fillAgent(resp, row);
        return resp;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ConversationSummaryResp update(String publicId, ConversationUpdateReq request) {
        if (request == null) {
            throw ConversationSupport.invalid("会话配置不能为空");
        }
        ConversationDO existing = lookup.requireOwned(publicId);
        ConversationDO target = new ConversationDO();
        target.setId(existing.getId());
        target.setPublicId(existing.getPublicId());
        target.setUserId(existing.getUserId());
        target.setAgentId(existing.getAgentId());
        target.setApiKeyId(existing.getApiKeyId());
        target.setTitle(request.getTitle() == null
                ? existing.getTitle() : ConversationSupport.title(request.getTitle()));
        target.setMessageCount(existing.getMessageCount());
        target.setLastMessageAt(existing.getLastMessageAt());
        target.setStatus(ConversationSupport.status(request.getStatus(), existing.getStatus()));
        conversations.updateById(target);
        ConversationSummaryResp resp = converter.toSummary(target, null, null);
        fillAgent(resp, target);
        return resp;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String publicId) {
        ConversationDO existing = lookup.requireOwned(publicId);
        if (conversations.deleteById(existing.getId()) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "会话不存在");
        }
    }

    @Override
    public List<ConversationMessageResp> listMessages(String publicId, Integer limit) {
        ConversationDO session = lookup.requireOwned(publicId);
        int max = ConversationSupport.messageLimit(limit);
        List<MessageDO> rows = messages.selectList(new QueryWrapper<MessageDO>()
                .eq("session_id", session.getId())
                .orderByDesc("created_at", "id")
                .last("LIMIT " + max));
        Collections.reverse(rows);
        return foldToolMessages(rows);
    }

    /**
     * 把工具消息折叠进它前面那条助手消息。
     *
     * <p>工具调用是助手那一轮的一部分，界面上要画成助手消息底下的卡片，
     * 不是一条独立的消息。所以出接口时合并掉。
     *
     * <p>折叠后一条助手消息最多带一组工具调用 —— 数据库里工具消息紧跟在
     * 发起它的助手消息后面，一轮里的多次工具调用是连续几条工具消息。
     */
    private List<ConversationMessageResp> foldToolMessages(List<MessageDO> rows) {
        List<ConversationMessageResp> result = new ArrayList<>();
        for (int index = 0; index < rows.size(); index++) {
            MessageDO row = rows.get(index);
            if (ConversationSupport.ROLE_TOOL.equals(row.getRole())) {
                // 前面没有可归属的消息（理论上不会发生），单独成条，别把工具结果丢了
                result.add(converter.toMessage(row));
                continue;
            }
            ConversationMessageResp resp = converter.toMessage(row);
            List<ConversationMessageResp.ToolCallView> calls = new ArrayList<>();
            int cursor = index + 1;
            while (cursor < rows.size() && ConversationSupport.ROLE_TOOL.equals(rows.get(cursor).getRole())) {
                calls.add(converter.toToolCallView(rows.get(cursor).getContent()));
                cursor++;
            }
            if (!calls.isEmpty()) {
                resp.setToolCalls(calls);
            }
            result.add(resp);
            index = cursor - 1;
        }
        return result;
    }

    /** 会话里记的 Agent 主键 → Agent 详情；解析不到就不填展示字段。 */
    private void fillAgent(ConversationSummaryResp resp, ConversationDO row) {
        if (row.getAgentId() == null) {
            return;
        }
        try {
            AgentDetailResp agent = agents.getById(row.getAgentId());
            resp.setAgentPublicId(agent.getPublicId());
            resp.setAgentName(agent.getName());
        } catch (BizException exception) {
            // Agent 被删了：会话还在，只是展示不出名字
        }
    }

    private void fillAgents(List<ConversationSummaryResp> items, List<ConversationDO> rows) {
        for (int index = 0; index < items.size(); index++) {
            fillAgent(items.get(index), rows.get(index));
        }
    }

    /** 运行记录列表的每页条数。 */
    private static int size(ConversationRunQuery query) {
        if (query.getSize() == null) {
            return PageQuery.DEFAULT_SIZE;
        }
        return Math.max(1, Math.min(query.getSize(), PageQuery.MAX_SIZE_ADMIN));
    }

    private static int size(ConversationListQuery query) {
        if (query.getSize() == null) {
            return PageQuery.DEFAULT_SIZE;
        }
        return Math.max(1, Math.min(query.getSize(), PageQuery.MAX_SIZE_ADMIN));
    }
}
