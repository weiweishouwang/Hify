package com.hify.ai.internal.controller;

import com.hify.ai.api.ProviderConfigResp;
import com.hify.ai.api.ProviderConnectionTestResp;
import com.hify.ai.api.ProviderCreateReq;
import com.hify.ai.api.ProviderDetailResp;
import com.hify.ai.api.ProviderListQuery;
import com.hify.ai.api.ProviderListResp;
import com.hify.ai.api.ProviderService;
import com.hify.ai.api.ProviderUpdateReq;
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

/** Provider HTTP 接入层：只绑定校验参数、调用服务、封装 Result，不访问 Mapper 或开启事务。 */
@RestController
@RequestMapping(value = "/api/v1/providers", produces = MediaType.APPLICATION_JSON_VALUE)
public class ProviderController {
    private static final String PUBLIC_ID_PATTERN = "[0-7][0-9A-HJKMNP-TV-Z]{25}";
    private static final String INVALID_ID = "id 必须是26位 ULID";
    private final ProviderService service;
    private final ProviderConnectionTestDispatcher connectionTests;

    public ProviderController(ProviderService service, ProviderConnectionTestDispatcher connectionTests) {
        this.service = service;
        this.connectionTests = connectionTests;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public Result<ProviderConfigResp> create(@Valid @RequestBody ProviderCreateReq request) {
        return Result.ok(service.create(request));
    }

    /** 沿用服务的 cursor/size 游标分页，不转换成 OFFSET 页码分页。 */
    @GetMapping
    public Result<ProviderListResp> list(@Valid @ModelAttribute ProviderListQuery query) {
        return Result.ok(service.list(query));
    }

    @GetMapping("/{id}")
    public Result<ProviderDetailResp> get(
            @Valid @PathVariable("id") @Pattern(regexp = PUBLIC_ID_PATTERN, message = INVALID_ID) String id) {
        return Result.ok(service.get(id));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Result<ProviderConfigResp> update(
            @Valid @PathVariable("id") @Pattern(regexp = PUBLIC_ID_PATTERN, message = INVALID_ID) String id,
            @Valid @RequestBody ProviderUpdateReq request) {
        return Result.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(
            @Valid @PathVariable("id") @Pattern(regexp = PUBLIC_ID_PATTERN, message = INVALID_ID) String id) {
        service.delete(id);
        return Result.ok();
    }

    /** Spring MVC 异步派发，最终 HTTP 响应体仍是 Result。 */
    @PostMapping("/{id}/test-connection")
    public DeferredResult<Result<ProviderConnectionTestResp>> testConnection(
            @Valid @PathVariable("id") @Pattern(regexp = PUBLIC_ID_PATTERN, message = INVALID_ID) String id) {
        return connectionTests.testConnection(id);
    }
}
