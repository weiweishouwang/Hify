package com.hify.ai.internal.controller;

import com.hify.ai.api.ModelListQuery;
import com.hify.ai.api.ModelListResp;
import com.hify.ai.api.ModelService;
import com.hify.ai.api.ModelSummaryResp;
import com.hify.ai.api.ModelSyncReq;
import com.hify.ai.api.ModelSyncResp;
import com.hify.ai.api.ModelUpdateReq;
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

/** 模型 HTTP 接入层：只绑定校验参数、调用服务、封装 Result。 */
@RestController
@RequestMapping(value = "/api/v1/models", produces = MediaType.APPLICATION_JSON_VALUE)
public class ModelController {

    private static final String PUBLIC_ID_PATTERN = "[0-7][0-9A-HJKMNP-TV-Z]{25}";
    private static final String INVALID_ID = "id 必须是26位 ULID";

    private final ModelService service;

    public ModelController(ModelService service) {
        this.service = service;
    }

    /** 沿用服务的 cursor/size 游标分页，不转换成 OFFSET 页码分页。 */
    @GetMapping
    public Result<ModelListResp> list(@Valid @ModelAttribute ModelListQuery query) {
        return Result.ok(service.list(query));
    }

    @GetMapping("/{id}")
    public Result<ModelSummaryResp> get(
            @Valid @PathVariable("id") @Pattern(regexp = PUBLIC_ID_PATTERN, message = INVALID_ID) String id) {
        return Result.ok(service.get(id));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Result<ModelSummaryResp> update(
            @Valid @PathVariable("id") @Pattern(regexp = PUBLIC_ID_PATTERN, message = INVALID_ID) String id,
            @Valid @RequestBody ModelUpdateReq request) {
        return Result.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(
            @Valid @PathVariable("id") @Pattern(regexp = PUBLIC_ID_PATTERN, message = INVALID_ID) String id) {
        service.delete(id);
        return Result.ok();
    }

    /** 从接入点同步上游模型目录；同步后会拉一次上游接口，耗时较长。 */
    @PostMapping(value = "/sync", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Result<ModelSyncResp> sync(@Valid @RequestBody ModelSyncReq request) {
        return Result.ok(service.sync(request.getProviderPublicId()));
    }
}
