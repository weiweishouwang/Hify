package com.hify.workflow.api;

/**
 * 工作流连线。
 *
 * @param from 起点节点 id
 * @param to   终点节点 id
 */
public record WorkflowEdge(String from, String to) {
}
