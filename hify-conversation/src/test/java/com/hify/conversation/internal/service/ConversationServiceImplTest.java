package com.hify.conversation.internal.service;

import com.hify.agent.api.AgentDetailResp;
import com.hify.agent.api.AgentService;
import com.hify.conversation.api.ConversationCreateReq;
import com.hify.conversation.api.ConversationListQuery;
import com.hify.conversation.api.ConversationUpdateReq;
import com.hify.conversation.internal.converter.ConversationConverter;
import com.hify.conversation.internal.entity.ConversationDO;
import com.hify.conversation.internal.entity.MessageDO;
import com.hify.conversation.internal.mapper.ConversationMapper;
import com.hify.conversation.internal.mapper.MessageMapper;
import com.hify.shared.api.CurrentUserService;
import com.hify.shared.exception.BizException;
import com.hify.shared.json.JsonUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 会话的增删改查：归属校验、标题、游标分页与消息历史。 */
@ExtendWith(MockitoExtension.class)
class ConversationServiceImplTest {

    @Mock ConversationMapper conversations;
    @Mock MessageMapper messages;
    @Mock ConversationLookup lookup;
    @Mock CurrentUserService currentUsers;
    @Mock AgentService agents;

    ConversationServiceImpl service;

    @BeforeEach
    void setUp() {
        JsonUtils json = new JsonUtils(new ObjectMapper());
        service = new ConversationServiceImpl(conversations, messages, lookup, currentUsers, agents,
                new ConversationConverter(json));
        lenient().when(currentUsers.currentUserId()).thenReturn(42L);
        lenient().when(agents.getById(any())).thenReturn(agent());
    }

    static AgentDetailResp agent() {
        var agent = new AgentDetailResp();
        agent.setId(3L);
        agent.setPublicId("01AGENT00000000000000000001");
        agent.setName("客服助手");
        return agent;
    }

    static ConversationDO entity() {
        var entity = new ConversationDO();
        entity.setId(1L);
        entity.setPublicId("01SESSION00000000000000001");
        entity.setUserId(42L);
        entity.setAgentId(3L);
        entity.setTitle("订单问题");
        entity.setMessageCount(4);
        entity.setStatus("active");
        entity.setCreatedAt(LocalDateTime.of(2026, 11, 1, 10, 0));
        entity.setUpdatedAt(LocalDateTime.of(2026, 11, 1, 10, 0));
        return entity;
    }

    @Test
    @DisplayName("创建：归属当前用户，绑定 Agent 的内部主键")
    void createStoresOwnerAndAgent() {
        when(agents.get("01AGENT00000000000000000001")).thenReturn(agent());
        var request = new ConversationCreateReq();
        request.setAgentPublicId("01AGENT00000000000000000001");
        request.setTitle("  订单问题  ");

        var resp = service.create(request);

        assertThat(resp.getTitle()).isEqualTo("订单问题");
        assertThat(resp.getAgentName()).isEqualTo("客服助手");
        assertThat(resp.getStatus()).isEqualTo("active");

        var captor = ArgumentCaptor.forClass(ConversationDO.class);
        verify(conversations).insert(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(42L);
        assertThat(captor.getValue().getAgentId()).isEqualTo(3L);
        assertThat(captor.getValue().getTitle()).isEqualTo("订单问题");
    }

    @Test
    @DisplayName("创建：Agent 不存在时报资源不存在")
    void createRejectsMissingAgent() {
        when(agents.get(anyString())).thenThrow(new BizException(
                com.hify.shared.exception.ErrorCode.NOT_FOUND, "Agent 不存在"));
        var request = new ConversationCreateReq();
        request.setAgentPublicId("01AGENT00000000000000000009");
        assertThatThrownBy(() -> service.create(request)).isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("列表：只列当前用户的会话，游标分页")
    void listIsScopedToCurrentUser() {
        when(conversations.selectList(any())).thenReturn(List.of(entity()));
        var resp = service.list(new ConversationListQuery());
        assertThat(resp.getItems()).hasSize(1);
        assertThat(resp.getHasNext()).isFalse();
        assertThat(resp.getItems().getFirst().getAgentName()).isEqualTo("客服助手");

        var captor = ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.query.QueryWrapper.class);
        verify(conversations).selectList(captor.capture());
        assertThat(captor.getValue().getTargetSql()).contains("user_id");
    }

    @Test
    @DisplayName("取详情：不属于当前用户时按「不存在」处理")
    void getRejectsForeignSession() {
        when(lookup.requireOwned(anyString())).thenThrow(new BizException(
                com.hify.shared.exception.ErrorCode.NOT_FOUND, "会话不存在"));
        assertThatThrownBy(() -> service.get("01SESSION00000000000000001"))
                .isInstanceOf(BizException.class)
                .satisfies(thrown -> assertThat(((BizException) thrown).getErrorCode())
                        .isEqualTo(com.hify.shared.exception.ErrorCode.NOT_FOUND));
    }

    @Test
    @DisplayName("更新：改标题与归档")
    void updateChangesTitleAndStatus() {
        when(lookup.requireOwned(anyString())).thenReturn(entity());
        var request = new ConversationUpdateReq();
        request.setTitle("新标题");
        request.setStatus("archived");

        var resp = service.update("01SESSION00000000000000001", request);

        assertThat(resp.getTitle()).isEqualTo("新标题");
        assertThat(resp.getStatus()).isEqualTo("archived");
    }

    @Test
    @DisplayName("更新：状态非法时拒绝")
    void updateRejectsBadStatus() {
        when(lookup.requireOwned(anyString())).thenReturn(entity());
        var request = new ConversationUpdateReq();
        request.setStatus("wat");
        assertThatThrownBy(() -> service.update("01SESSION00000000000000001", request))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("会话状态");
    }

    @Test
    @DisplayName("删除：按主键逻辑删除")
    void deleteSoftDeletes() {
        when(lookup.requireOwned(anyString())).thenReturn(entity());
        when(conversations.deleteById(any(java.io.Serializable.class))).thenReturn(1);
        service.delete("01SESSION00000000000000001");
        verify(conversations).deleteById(1L);
    }

    @Test
    @DisplayName("消息历史：倒序取最近若干条再翻正")
    void messagesAreReturnedInChronologicalOrder() {
        when(lookup.requireOwned(anyString())).thenReturn(entity());
        var second = message(2L, "assistant", "您好");
        var first = message(1L, "user", "订单在哪");
        when(messages.selectList(any())).thenReturn(new java.util.ArrayList<>(List.of(second, first)));

        var resp = service.listMessages("01SESSION00000000000000001", 50);

        assertThat(resp).hasSize(2);
        assertThat(resp.getFirst().getRole()).isEqualTo("user");
        assertThat(resp.get(1).getRole()).isEqualTo("assistant");
    }

    static MessageDO message(Long id, String role, String content) {
        var message = new MessageDO();
        message.setId(id);
        message.setSessionId(1L);
        message.setRole(role);
        message.setContent(content);
        message.setContentType("text");
        message.setTokenCount(0);
        message.setCreatedAt(LocalDateTime.of(2026, 11, 1, 10, 0).plusMinutes(id));
        return message;
    }
}
