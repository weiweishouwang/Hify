package com.hify.workflow.api;

import java.util.List;
import java.util.Map;

/**
 * 一次工作流运行的结果。
 *
 * @param status       状态：succeeded / failed
 * @param output       出参：各 output 节点的「输出变量名 → 值」
 * @param stepCount    执行了几个节点
 * @param latencyMs    总耗时（毫秒）
 * @param errorMessage 失败原因；成功时为 null
 * @param steps        执行轨迹（只在本��响应里给，不落库 —— wf_run 没有存轨迹的列）
 */
public record WorkflowRunResp(
        String status,
        Map<String, Object> output,
        Integer stepCount,
        Integer latencyMs,
        String errorMessage,
        List<WorkflowStep> steps) {

    /**
     * 一个节点的执行痕迹。
     *
     * @param nodeId    节点 id
     * @param type      节点类型
     * @param name      节点名
     * @param output    输出值（截断后的预览）
     * @param latencyMs 该节点的耗时
     */
    public record WorkflowStep(String nodeId, String type, String name, String output, Integer latencyMs) {
    }
}
