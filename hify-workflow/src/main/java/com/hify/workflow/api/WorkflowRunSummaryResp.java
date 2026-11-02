package com.hify.workflow.api;

import java.time.LocalDateTime;

/**
 * 一次运行的摘要（运行记录列表用）。
 *
 * @param id           运行主键
 * @param status       状态：running / succeeded / failed
 * @param stepCount    执行了几个节点
 * @param latencyMs    总耗时（毫秒）
 * @param errorMessage 失败原因；成功时为 null
 * @param createdAt    创建时间(UTC)
 */
public record WorkflowRunSummaryResp(
        Long id,
        String status,
        Integer stepCount,
        Integer latencyMs,
        String errorMessage,
        LocalDateTime createdAt) {
}
