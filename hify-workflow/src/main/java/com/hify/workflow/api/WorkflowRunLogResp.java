package com.hify.workflow.api;

import java.time.LocalDateTime;

/**
 * 一次工作流运行的日志条目（控制台日志页用）。
 *
 * <p>比 {@link WorkflowRunSummaryResp} 多两样：跑的是哪个工作流、入参出参的摘要。
 * 入参出参可能很大，日志页只放截断后的摘要。
 *
 * @param id                运行主键
 * @param definitionPublicId 工作流的对外 ID
 * @param definitionName    工作流名字
 * @param status            状态：running / succeeded / failed
 * @param stepCount         执行了几个节点
 * @param latencyMs         总耗时（毫秒）
 * @param inputPreview      入参摘要
 * @param outputPreview     出参摘要
 * @param errorMessage      失败原因；成功时为 null
 * @param createdAt         创建时间(UTC)
 */
public record WorkflowRunLogResp(
        Long id,
        String definitionPublicId,
        String definitionName,
        String status,
        Integer stepCount,
        Integer latencyMs,
        String inputPreview,
        String outputPreview,
        String errorMessage,
        LocalDateTime createdAt) {
}
