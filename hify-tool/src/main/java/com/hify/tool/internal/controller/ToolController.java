package com.hify.tool.internal.controller;

import com.hify.shared.web.Result;
import com.hify.tool.api.ToolCreateReq;
import com.hify.tool.api.ToolDefinitionService;
import com.hify.tool.api.ToolDetailResp;
import com.hify.tool.api.ToolInvokeReq;
import com.hify.tool.api.ToolInvokeResp;
import com.hify.tool.api.ToolListQuery;
import com.hify.tool.api.ToolListResp;
import com.hify.tool.api.ToolSyncResp;
import com.hify.tool.api.ToolUpdateReq;
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

/**
 * 工具 HTTP 接入层：只绑定校验参数、调用服务、封装 Result，不访问 Mapper 或开启事务。
 */
@RestController
@RequestMapping(value = "/api/v1/tools", produces = MediaType.APPLICATION_JSON_VALUE)
public class ToolController {

    private static final String PUBLIC_ID_PATTERN = "[0-7][0-9A-HJKMNP-TV-Z]{25}";
    private static final String INVALID_ID = "id 必须是26位 ULID";

    private final ToolDefinitionService service;
    private final ToolInvokeDispatcher invocations;

    public ToolController(ToolDefinitionService service, ToolInvokeDispatcher invocations) {
        this.service = service;
        this.invocations = invocations;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public Result<ToolDetailResp> create(@Valid @RequestBody ToolCreateReq request) {
        return Result.ok(service.create(request));
    }

    /** 沿用服务的 cursor/size 游标分页，不转换成 OFFSET 页码分页。 */
    @GetMapping
    public Result<ToolListResp> list(@Valid @ModelAttribute ToolListQuery query) {
        return Result.ok(service.list(query));
    }

    @GetMapping("/{id}")
    public Result<ToolDetailResp> get(
            @Valid @PathVariable("id") @Pattern(regexp = PUBLIC_ID_PATTERN, message = INVALID_ID) String id) {
        return Result.ok(service.get(id));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Result<ToolDetailResp> update(
            @Valid @PathVariable("id") @Pattern(regexp = PUBLIC_ID_PATTERN, message = INVALID_ID) String id,
            @Valid @RequestBody ToolUpdateReq request) {
        return Result.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(
            @Valid @PathVariable("id") @Pattern(regexp = PUBLIC_ID_PATTERN, message = INVALID_ID) String id) {
        service.delete(id);
        return Result.ok();
    }

    /** 从各来源重新发现工具并同步进目录。 */
    @PostMapping("/sync")
    public Result<ToolSyncResp> sync() {
        return Result.ok(service.sync());
    }

    /** 手工测试一个工具；Spring MVC 异步派发，最终 HTTP 响应体仍是 Result。 */
    @PostMapping(value = "/{id}/test", consumes = MediaType.APPLICATION_JSON_VALUE)
    public DeferredResult<Result<ToolInvokeResp>> test(
            @Valid @PathVariable("id") @Pattern(regexp = PUBLIC_ID_PATTERN, message = INVALID_ID) String id,
            @Valid @RequestBody(required = false) ToolInvokeReq request) {
        return invocations.test(id, request == null ? null : request.getArgs());
    }
}
