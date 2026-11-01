package com.hify.mcp.internal.controller;

import com.hify.mcp.api.McpConnectionTestResp;
import com.hify.mcp.api.McpServerCreateReq;
import com.hify.mcp.api.McpServerListQuery;
import com.hify.mcp.api.McpServerListResp;
import com.hify.mcp.api.McpServerService;
import com.hify.mcp.api.McpServerSummaryResp;
import com.hify.mcp.api.McpServerUpdateReq;
import com.hify.mcp.api.McpToolSyncResp;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;

/** MCP 服务 HTTP 接入层：只绑定校验参数、调用服务、封装 Result。 */
@RestController
@RequestMapping(value = "/api/v1/mcp-servers", produces = MediaType.APPLICATION_JSON_VALUE)
public class McpServerController {

    private static final String PUBLIC_ID_PATTERN = "[0-7][0-9A-HJKMNP-TV-Z]{25}";
    private static final String INVALID_ID = "id 必须是26位 ULID";

    private final McpServerService service;
    private final McpServerDispatcher dispatcher;

    public McpServerController(McpServerService service, McpServerDispatcher dispatcher) {
        this.service = service;
        this.dispatcher = dispatcher;
    }

    /** 沿用服务的 cursor/size 游标分页。 */
    @GetMapping
    public Result<McpServerListResp> list(@Valid @ModelAttribute McpServerListQuery query) {
        return Result.ok(service.list(query));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public Result<McpServerSummaryResp> create(@Valid @RequestBody McpServerCreateReq request) {
        return Result.ok(service.create(request));
    }

    @GetMapping("/{id}")
    public Result<McpServerSummaryResp> get(
            @Valid @PathVariable("id") @Pattern(regexp = PUBLIC_ID_PATTERN, message = INVALID_ID) String id) {
        return Result.ok(service.get(id));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Result<McpServerSummaryResp> update(
            @Valid @PathVariable("id") @Pattern(regexp = PUBLIC_ID_PATTERN, message = INVALID_ID) String id,
            @Valid @RequestBody McpServerUpdateReq request) {
        return Result.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(
            @Valid @PathVariable("id") @Pattern(regexp = PUBLIC_ID_PATTERN, message = INVALID_ID) String id) {
        service.delete(id);
        return Result.ok();
    }

    /** 连上去试一次；会发起上游调用，异步派发。 */
    @PostMapping("/{id}/test")
    public DeferredResult<Result<McpConnectionTestResp>> test(
            @Valid @PathVariable("id") @Pattern(regexp = PUBLIC_ID_PATTERN, message = INVALID_ID) String id) {
        return dispatcher.test(id);
    }

    /** 刷新所有启用服务的工具缓存；会发起上游调用，异步派发。 */
    @PostMapping("/sync")
    public DeferredResult<Result<McpToolSyncResp>> sync() {
        return dispatcher.sync();
    }
}
