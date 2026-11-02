package com.hify.workflow.api;

import java.util.List;

/**
 * 工作流定义的存取与执行。
 *
 * <p>工作流是<b>编排层</b>：定义是用户手写的 JSON，执行按连线走。
 * 它和 `hify-conversation` 是平行的两个编排层，互不依赖。
 */
public interface WorkflowService {

    /**
     * 创建工作流。
     *
     * @param request 创建请求
     * @return 创建后的工作流详情
     */
    WorkflowDetailResp create(WorkflowCreateReq request);

    /**
     * 按游标分页列出工作流。
     *
     * @param query 查询条件
     * @return 一页工作流
     */
    WorkflowListResp list(WorkflowListQuery query);

    /**
     * 取工作流详情。
     *
     * @param publicId 工作流的对外 ID
     * @return 工作流详情
     */
    WorkflowDetailResp get(String publicId);

    /**
     * 更新工作流。
     *
     * @param publicId 工作流的对外 ID
     * @param request  更新请求
     * @return 更新后的工作流详情
     */
    WorkflowDetailResp update(String publicId, WorkflowUpdateReq request);

    /**
     * 删除工作流（逻辑删除）。运行记录保留 —— 它们是事件流。
     *
     * @param publicId 工作流的对外 ID
     */
    void delete(String publicId);

    /**
     * 运行工作流。
     *
     * <p><b>阻塞的</b>：要等跑完才返回。调用方要放在专用线程上跑
     * （CLAUDE.md §3.5 的阻塞调用约定）。
     *
     * @param publicId 工作流的对外 ID
     * @param request  运行请求（入参）
     * @param userId   调用者用户 id；<b>显式携带</b> —— 执行跑在线程池里，
     *                  那边的 ThreadLocal 是空的（CLAUDE.md §3.1 C5）
     * @return 运行结果
     */
    WorkflowRunResp run(String publicId, WorkflowRunReq request, Long userId);

    /**
     * 列出工作流的运行记录，按时间倒序。
     *
     * @param publicId 工作流的对外 ID
     * @param limit    最多返回多少条
     * @return 运行记录
     */
    List<WorkflowRunSummaryResp> listRuns(String publicId, Integer limit);

    /**
     * 按游标分页列出全部工作流的运行记录。
     *
     * <p>控制台「日志」页用 —— 跨工作流看最近跑过什么。
     *
     * @param query 查询条件
     * @return 一页运行记录
     */
    WorkflowRunLogListResp listRecentRuns(WorkflowRunLogQuery query);
}
