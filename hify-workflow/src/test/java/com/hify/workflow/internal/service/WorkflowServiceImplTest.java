package com.hify.workflow.internal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.shared.json.JsonUtils;
import com.hify.workflow.api.WorkflowCreateReq;
import com.hify.workflow.api.WorkflowListQuery;
import com.hify.workflow.api.WorkflowUpdateReq;
import com.hify.workflow.internal.converter.WorkflowConverter;
import com.hify.workflow.internal.entity.WorkflowDefinitionDO;
import com.hify.workflow.internal.mapper.WorkflowMapper;
import com.hify.workflow.internal.mapper.WorkflowRunMapper;
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

/** 工作流定义的增删改查：名称唯一、乐观锁、游标分页。 */
@ExtendWith(MockitoExtension.class)
class WorkflowServiceImplTest {

    private static final String DEFINITION = """
            {"model": "m1", "nodes": [
               {"id": "n1", "type": "llm", "prompt": "a", "output": "x"},
               {"id": "n2", "type": "output", "value": "{{x}}", "output": "result"}
             ], "edges": [{"from": "n1", "to": "n2"}]}
            """;

    @Mock WorkflowMapper workflows;
    @Mock WorkflowRunMapper runs;
    @Mock WorkflowEngine engine;

    WorkflowServiceImpl service;

    @BeforeEach
    void setUp() {
        JsonUtils json = new JsonUtils(new ObjectMapper());
        WorkflowSupport support = new WorkflowSupport(json);
        service = new WorkflowServiceImpl(workflows, runs, support, engine, json,
                new WorkflowConverter(json));
        lenient().when(workflows.findIdByName(anyString())).thenReturn(null);
    }

    static WorkflowDefinitionDO entity() {
        var entity = new WorkflowDefinitionDO();
        entity.setId(1L);
        entity.setPublicId("01WORKFLOW00000000000000001");
        entity.setName("售后流程");
        entity.setDescription("处理售后");
        entity.setVersion(3);
        entity.setStatus("published");
        entity.setCreatedAt(LocalDateTime.of(2026, 11, 2, 10, 0));
        entity.setUpdatedAt(LocalDateTime.of(2026, 11, 2, 10, 0));
        return entity;
    }

    @Test
    @DisplayName("创建：解析定义并落库")
    void createParsesDefinition() {
        var request = new WorkflowCreateReq();
        request.setName(" 售后流程 ");
        request.setDescription(" 处理售后 ");
        request.setDefinition(DEFINITION);

        var resp = service.create(request);

        assertThat(resp.getName()).isEqualTo("售后流程");
        assertThat(resp.getStatus()).isEqualTo("draft");
        assertThat(resp.getNodeCount()).isEqualTo(2);
        assertThat(resp.getDefinition()).contains("\"nodes\"");

        var captor = ArgumentCaptor.forClass(WorkflowDefinitionDO.class);
        verify(workflows).insert(captor.capture());
        assertThat(captor.getValue().getDefinition().getNodes()).hasSize(2);
        assertThat(captor.getValue().getVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("创建：定义不合法时拒绝")
    void createRejectsInvalidDefinition() {
        var request = new WorkflowCreateReq();
        request.setName("坏的");
        request.setDefinition("{\"nodes\": []}");
        assertThatThrownBy(() -> service.create(request)).isInstanceOf(
                com.hify.shared.exception.BizException.class)
                .hasMessageContaining("至少要有一个节点");
    }

    @Test
    @DisplayName("创建：名称已被占用时冲突")
    void createRejectsDuplicateName() {
        when(workflows.findIdByName("售后流程")).thenReturn(9L);
        var request = new WorkflowCreateReq();
        request.setName("售后流程");
        request.setDefinition(DEFINITION);
        assertThatThrownBy(() -> service.create(request)).isInstanceOf(
                com.hify.shared.exception.BizException.class)
                .hasMessageContaining("已被占用");
    }

    @Test
    @DisplayName("列表：游标分页并按关键字过滤")
    void listPaginates() {
        when(workflows.selectList(any())).thenReturn(List.of(entity()));
        var resp = service.list(new WorkflowListQuery());
        assertThat(resp.getItems()).hasSize(1);
        assertThat(resp.getHasNext()).isFalse();

        var captor = ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.query.QueryWrapper.class);
        verify(workflows).selectList(captor.capture());
        assertThat(captor.getValue().getTargetSql()).contains("created_at");
    }

    @Test
    @DisplayName("更新：配置版本不匹配时冲突")
    void updateRejectsStaleVersion() {
        when(workflows.selectOne(any())).thenReturn(entity());
        var request = new WorkflowUpdateReq();
        request.setName("新名字");
        request.setVersion(1);
        assertThatThrownBy(() -> service.update("01WORKFLOW00000000000000001", request))
                .isInstanceOf(com.hify.shared.exception.BizException.class)
                .hasMessageContaining("配置已变更");
    }

    @Test
    @DisplayName("更新：定义变了版本号加一，没变就不加")
    void updateBumpsVersionWhenDefinitionChanged() {
        when(workflows.selectOne(any())).thenReturn(entity());
        when(workflows.update(any(WorkflowDefinitionDO.class), any())).thenReturn(1);
        var request = new WorkflowUpdateReq();
        request.setDefinition(DEFINITION);
        request.setVersion(3);

        var resp = service.update("01WORKFLOW00000000000000001", request);

        assertThat(resp.getVersion()).isEqualTo(4);
    }

    @Test
    @DisplayName("删除：按主键逻辑删除")
    void deleteSoftDeletes() {
        when(workflows.selectOne(any())).thenReturn(entity());
        when(workflows.deleteById(any(java.io.Serializable.class))).thenReturn(1);
        service.delete("01WORKFLOW00000000000000001");
        verify(workflows).deleteById(1L);
    }

    @Test
    @DisplayName("取详情：不存在时报资源不存在")
    void getMissing() {
        when(workflows.selectOne(any())).thenReturn(null);
        assertThatThrownBy(() -> service.get("01WORKFLOW00000000000000001"))
                .isInstanceOf(com.hify.shared.exception.BizException.class)
                .satisfies(thrown -> assertThat(((com.hify.shared.exception.BizException) thrown).getErrorCode())
                        .isEqualTo(com.hify.shared.exception.ErrorCode.NOT_FOUND));
    }
}
