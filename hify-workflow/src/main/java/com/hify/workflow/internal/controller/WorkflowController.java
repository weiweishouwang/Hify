package com.hify.workflow.internal.controller;

import com.hify.workflow.api.WorkflowCreateReq;
import com.hify.workflow.api.WorkflowDetailResp;
import com.hify.workflow.api.WorkflowListQuery;
import com.hify.workflow.api.WorkflowListResp;
import com.hify.workflow.api.WorkflowRunLogListResp;
import com.hify.workflow.api.WorkflowRunLogQuery;
import com.hify.workflow.api.WorkflowRunReq;
import com.hify.workflow.api.WorkflowRunResp;
import com.hify.workflow.api.WorkflowRunSummaryResp;
import com.hify.workflow.api.WorkflowService;
import com.hify.workflow.api.WorkflowSummaryResp;
import com.hify.workflow.api.WorkflowUpdateReq;
import com.hify.shared.web.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.List;

/** 工作流的 HTTP 接入层：只绑定校验参数、调用服务、封装 Result。 */
@RestController
@RequestMapping(value = "/api/v1/workflows", produces = MediaType.APPLICATION_JSON_VALUE)
public class WorkflowController {

    private static final String PUBLIC_ID_PATTERN = "[0-7][0-9A-HJKMNP-TV-Z]{25}";
    private static final String INVALID_ID = "id 必须是26位 ULID";

    private final WorkflowService service;
    private final WorkflowRunDispatcher dispatcher;

    public WorkflowController(WorkflowService service, WorkflowRunDispatcher dispatcher) {
        this.service = service;
        this.dispatcher = dispatcher;
    }

    /** 沿用服务的 cursor/size 游标分页。 */
    @GetMapping
    public Result<WorkflowListResp> list(@Valid @ModelAttribute WorkflowListQuery query) {
        return Result.ok(service.list(query));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public Result<WorkflowDetailResp> create(@Valid @RequestBody WorkflowCreateReq request) {
        return Result.ok(service.create(request));
    }

    @GetMapping("/{id}")
    public Result<WorkflowDetailResp> get(
            @Valid @PathVariable("id") @Pattern(regexp = PUBLIC_ID_PATTERN, message = INVALID_ID) String id) {
        return Result.ok(service.get(id));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Result<WorkflowDetailResp> update(
            @Valid @PathVariable("id") @Pattern(regexp = PUBLIC_ID_PATTERN, message = INVALID_ID) String id,
            @Valid @RequestBody WorkflowUpdateReq request) {
        return Result.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(
            @Valid @PathVariable("id") @Pattern(regexp = PUBLIC_ID_PATTERN, message = INVALID_ID) String id) {
        service.delete(id);
        return Result.ok();
    }

    /**
     * 运行工作流。
     *
     * <p><b>异步派发</b>：工作流里有阻塞的模型调用，不能占着 Tomcat 线程
     * （CLAUDE.md §3.5）。
     */
    @PostMapping(value = "/{id}/run", consumes = MediaType.APPLICATION_JSON_VALUE)
    public DeferredResult<Result<WorkflowRunResp>> run(
            @Valid @PathVariable("id") @Pattern(regexp = PUBLIC_ID_PATTERN, message = INVALID_ID) String id,
            @Valid @RequestBody(required = false) WorkflowRunReq request) {
        return dispatcher.run(id, request);
    }

    /**
     * 全部工作流的运行记录（控制台日志页）。
     *
     * <p>路径是字面量 {@code /runs}，比 {@code /{id}/runs} 更具体。
     */
    @GetMapping("/runs")
    public Result<WorkflowRunLogListResp> recentRuns(@Valid @ModelAttribute WorkflowRunLogQuery query) {
        return Result.ok(service.listRecentRuns(query));
    }

    /** 工作流的运行记录，按时间倒序。 */
    @GetMapping("/{id}/runs")
    public Result<List<WorkflowRunSummaryResp>> runs(
            @Valid @PathVariable("id") @Pattern(regexp = PUBLIC_ID_PATTERN, message = INVALID_ID) String id,
            @RequestParam(required = false) Integer limit) {
        return Result.ok(service.listRuns(id, limit));
    }
}
