package com.hify.workflow.internal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.shared.exception.BizException;
import com.hify.shared.json.JsonUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 工作流定义的校验：节点字段、连线完整、无环、恰有入口。 */
class WorkflowSupportTest {

    private final WorkflowSupport support = new WorkflowSupport(new JsonUtils(new ObjectMapper()));

    static final String VALID = """
            {
              "model": "01MODEL0000000000000000001",
              "nodes": [
                {"id": "n1", "type": "llm", "prompt": "回答 {{input.q}}", "output": "answer"},
                {"id": "n2", "type": "if", "left": "{{answer}}", "operator": "contains",
                 "right": "好", "then": "n3", "otherwise": "n4"},
                {"id": "n3", "type": "output", "value": "{{answer}}", "output": "result"},
                {"id": "n4", "type": "output", "value": "没找到", "output": "result"}
              ],
              "edges": [{"from": "n1", "to": "n2"}]
            }
            """;

    @Test
    @DisplayName("合法定义通过校验")
    void validDefinition() {
        assertThatCode(() -> support.parse(VALID)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("节点 id 重复时拒绝")
    void duplicateNodeId() {
        String json = """
                {"nodes": [
                   {"id": "n1", "type": "llm", "prompt": "a", "output": "x", "model": "m"},
                   {"id": "n1", "type": "output", "value": "b"}
                 ], "edges": []}
                """;
        assertThatThrownBy(() -> support.parse(json)).isInstanceOf(BizException.class)
                .hasMessageContaining("节点 id 重复");
    }

    @Test
    @DisplayName("未知节点类型时拒绝")
    void unknownNodeType() {
        String json = """
                {"nodes": [{"id": "n1", "type": "魔法", "output": "x"}], "edges": []}
                """;
        assertThatThrownBy(() -> support.parse(json)).isInstanceOf(BizException.class)
                .hasMessageContaining("不支持的节点类型");
    }

    @Test
    @DisplayName("llm 节点缺 prompt 时拒绝")
    void llmWithoutPrompt() {
        String json = """
                {"model": "m", "nodes": [{"id": "n1", "type": "llm", "output": "x"}], "edges": []}
                """;
        assertThatThrownBy(() -> support.parse(json)).isInstanceOf(BizException.class)
                .hasMessageContaining("prompt");
    }

    @Test
    @DisplayName("llm 节点没有模型时拒绝")
    void llmWithoutModel() {
        String json = """
                {"nodes": [{"id": "n1", "type": "llm", "prompt": "a", "output": "x"}], "edges": []}
                """;
        assertThatThrownBy(() -> support.parse(json)).isInstanceOf(BizException.class)
                .hasMessageContaining("model");
    }

    @Test
    @DisplayName("连线的节点不存在时拒绝")
    void edgeToUnknownNode() {
        String json = """
                {"model": "m", "nodes": [
                   {"id": "n1", "type": "llm", "prompt": "a", "output": "x"},
                   {"id": "n2", "type": "output", "value": "b"}
                 ], "edges": [{"from": "n1", "to": "n9"}]}
                """;
        assertThatThrownBy(() -> support.parse(json)).isInstanceOf(BizException.class)
                .hasMessageContaining("终点节点不存在");
    }

    @Test
    @DisplayName("有环时拒绝")
    void cyclicDefinition() {
        String json = """
                {"model": "m", "nodes": [
                   {"id": "n1", "type": "llm", "prompt": "a", "output": "x"},
                   {"id": "n2", "type": "llm", "prompt": "b", "output": "y"},
                   {"id": "n3", "type": "output", "value": "c"}
                 ], "edges": [{"from": "n1", "to": "n2"}, {"from": "n2", "to": "n1"}]}
                """;
        assertThatThrownBy(() -> support.parse(json)).isInstanceOf(BizException.class)
                .hasMessageContaining("环");
    }

    @Test
    @DisplayName("多个入口节点时拒绝")
    void multipleEntries() {
        String json = """
                {"model": "m", "nodes": [
                   {"id": "n1", "type": "llm", "prompt": "a", "output": "x"},
                   {"id": "n2", "type": "llm", "prompt": "b", "output": "y"},
                   {"id": "n3", "type": "output", "value": "c"}
                 ], "edges": [{"from": "n1", "to": "n3"}, {"from": "n2", "to": "n3"}]}
                """;
        assertThatThrownBy(() -> support.parse(json)).isInstanceOf(BizException.class)
                .hasMessageContaining("入口节点");
    }

    @Test
    @DisplayName("没有 output 节点时拒绝")
    void withoutOutputNode() {
        String json = """
                {"model": "m", "nodes": [{"id": "n1", "type": "llm", "prompt": "a", "output": "x"}],
                 "edges": []}
                """;
        assertThatThrownBy(() -> support.parse(json)).isInstanceOf(BizException.class)
                .hasMessageContaining("output 节点");
    }

    @Test
    @DisplayName("输出变量名重复时拒绝")
    void duplicateOutputName() {
        String json = """
                {"model": "m", "nodes": [
                   {"id": "n1", "type": "llm", "prompt": "a", "output": "x"},
                   {"id": "n2", "type": "llm", "prompt": "b", "output": "x"}
                 ], "edges": [{"from": "n1", "to": "n2"}]}
                """;
        assertThatThrownBy(() -> support.parse(json)).isInstanceOf(BizException.class)
                .hasMessageContaining("输出变量名重复");
    }

    @Test
    @DisplayName("条件分支的走向写进 edges 时拒绝")
    void ifBranchMustNotBeInEdges() {
        String json = """
                {"model": "m", "nodes": [
                   {"id": "n1", "type": "if", "left": "a", "operator": "equals", "right": "b",
                    "then": "n2", "otherwise": "n3"},
                   {"id": "n2", "type": "output", "value": "c"},
                   {"id": "n3", "type": "output", "value": "d"}
                 ], "edges": [{"from": "n1", "to": "n2"}]}
                """;
        assertThatThrownBy(() -> support.parse(json)).isInstanceOf(BizException.class)
                .hasMessageContaining("then / otherwise");
    }

    @Test
    @DisplayName("不是合法 JSON 时拒绝")
    void invalidJson() {
        assertThatThrownBy(() -> support.parse("{不是 JSON")).isInstanceOf(BizException.class)
                .hasMessageContaining("不是合法 JSON");
    }

    @Test
    @DisplayName("入口节点是入度为 0 的那个")
    void entryNode() {
        var definition = support.parse(VALID);
        assertThat(WorkflowSupport.entryNodeId(definition)).isEqualTo("n1");
    }
}
