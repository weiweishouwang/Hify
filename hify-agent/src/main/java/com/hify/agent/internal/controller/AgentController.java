package com.hify.agent.internal.controller;

import com.hify.agent.api.AgentBundle;
import com.hify.agent.api.AgentCatalog;
import com.hify.agent.api.AgentCreateReq;
import com.hify.agent.api.AgentDetailResp;
import com.hify.agent.api.AgentListQuery;
import com.hify.agent.api.AgentListResp;
import com.hify.agent.api.AgentOptions;
import com.hify.agent.api.AgentService;
import com.hify.agent.api.AgentUpdateReq;
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

/** Agent 配置的 HTTP 接入层：只绑定校验参数、调用服务、封装 Result。 */
@RestController
@RequestMapping(value = "/api/v1/agents", produces = MediaType.APPLICATION_JSON_VALUE)
public class AgentController {

    private static final String PUBLIC_ID_PATTERN = "[0-7][0-9A-HJKMNP-TV-Z]{25}";
    private static final String INVALID_ID = "id 必须是26位 ULID";

    private final AgentService service;
    private final AgentCatalog catalog;

    public AgentController(AgentService service, AgentCatalog catalog) {
        this.service = service;
        this.catalog = catalog;
    }

    /** 沿用服务的 cursor/size 游标分页。 */
    @GetMapping
    public Result<AgentListResp> list(@Valid @ModelAttribute AgentListQuery query) {
        return Result.ok(service.list(query));
    }

    /** 新建 / 编辑 Agent 时可选项：模型、工具、知识库。 */
    @GetMapping("/options")
    public Result<AgentOptions> options() {
        return Result.ok(service.options());
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public Result<AgentDetailResp> create(@Valid @RequestBody AgentCreateReq request) {
        return Result.ok(service.create(request));
    }

    @GetMapping("/{id}")
    public Result<AgentDetailResp> get(
            @Valid @PathVariable("id") @Pattern(regexp = PUBLIC_ID_PATTERN, message = INVALID_ID) String id) {
        return Result.ok(service.get(id));
    }

    /**
     * 组装结果预览：模型、工具规格、知识库、提示词与参数区。
     *
     * <p>这是 {@link com.hify.agent.api.AgentCatalog} 的只读出口 ——
     * 管理台能看清「这个 Agent 实际会拿什么去跑」，编排层拿的是同一份组装结果。
     * 只有已发布的 Agent 能组装出来。
     */
    @GetMapping("/{id}/bundle")
    public Result<AgentBundle> bundle(
            @Valid @PathVariable("id") @Pattern(regexp = PUBLIC_ID_PATTERN, message = INVALID_ID) String id) {
        return Result.ok(catalog.assemble(id));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Result<AgentDetailResp> update(
            @Valid @PathVariable("id") @Pattern(regexp = PUBLIC_ID_PATTERN, message = INVALID_ID) String id,
            @Valid @RequestBody AgentUpdateReq request) {
        return Result.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(
            @Valid @PathVariable("id") @Pattern(regexp = PUBLIC_ID_PATTERN, message = INVALID_ID) String id) {
        service.delete(id);
        return Result.ok();
    }
}
