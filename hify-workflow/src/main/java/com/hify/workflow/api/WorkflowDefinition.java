package com.hify.workflow.api;

import java.util.List;

/**
 * 工作流定义：节点与连线。
 *
 * <p>这是 {@code wf_definition.definition} 列里 JSON 的类型化模型。
 * 用户手写 JSON，服务端解析成这个结构来校验与执行。
 */
public class WorkflowDefinition {

    /** 默认模型的 publicId；{@code llm} 节点可以用自己的 {@code model} 覆盖。 */
    private String model;

    /** 节点列表。 */
    private List<WorkflowNode> nodes;

    /** 连线列表。 */
    private List<WorkflowEdge> edges;

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public List<WorkflowNode> getNodes() {
        return nodes;
    }

    public void setNodes(List<WorkflowNode> nodes) {
        this.nodes = nodes;
    }

    public List<WorkflowEdge> getEdges() {
        return edges;
    }

    public void setEdges(List<WorkflowEdge> edges) {
        this.edges = edges;
    }
}
