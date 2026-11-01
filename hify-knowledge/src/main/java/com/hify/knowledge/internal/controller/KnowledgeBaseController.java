package com.hify.knowledge.internal.controller;

import com.hify.knowledge.api.EmbeddingModelOption;
import com.hify.knowledge.api.KnowledgeBaseCreateReq;
import com.hify.knowledge.api.KnowledgeBaseListQuery;
import com.hify.knowledge.api.KnowledgeBaseListResp;
import com.hify.knowledge.api.KnowledgeBaseService;
import com.hify.knowledge.api.KnowledgeBaseSummaryResp;
import com.hify.knowledge.api.KnowledgeBaseUpdateReq;
import com.hify.knowledge.api.SearchQuery;
import com.hify.knowledge.api.SearchResult;
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

import java.util.List;

/** 知识库 HTTP 接入层：只绑定校验参数、调用服务、封装 Result。 */
@RestController
@RequestMapping(value = "/api/v1/knowledge-bases", produces = MediaType.APPLICATION_JSON_VALUE)
public class KnowledgeBaseController {

    private static final String PUBLIC_ID_PATTERN = "[0-7][0-9A-HJKMNP-TV-Z]{25}";
    private static final String INVALID_ID = "id 必须是26位 ULID";

    private final KnowledgeBaseService service;
    private final KnowledgeSearchDispatcher searchDispatcher;

    public KnowledgeBaseController(KnowledgeBaseService service, KnowledgeSearchDispatcher searchDispatcher) {
        this.service = service;
        this.searchDispatcher = searchDispatcher;
    }

    /** 沿用服务的 cursor/size 游标分页，不转换成 OFFSET 页码分页。 */
    @GetMapping
    public Result<KnowledgeBaseListResp> list(@Valid @ModelAttribute KnowledgeBaseListQuery query) {
        return Result.ok(service.list(query));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public Result<KnowledgeBaseSummaryResp> create(@Valid @RequestBody KnowledgeBaseCreateReq request) {
        return Result.ok(service.create(request));
    }

    /** 可选的 embedding 模型，建库时用。 */
    @GetMapping("/embedding-models")
    public Result<List<EmbeddingModelOption>> embeddingModels() {
        return Result.ok(service.listEmbeddingModels());
    }

    @GetMapping("/{id}")
    public Result<KnowledgeBaseSummaryResp> get(
            @Valid @PathVariable("id") @Pattern(regexp = PUBLIC_ID_PATTERN, message = INVALID_ID) String id) {
        return Result.ok(service.get(id));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Result<KnowledgeBaseSummaryResp> update(
            @Valid @PathVariable("id") @Pattern(regexp = PUBLIC_ID_PATTERN, message = INVALID_ID) String id,
            @Valid @RequestBody KnowledgeBaseUpdateReq request) {
        return Result.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(
            @Valid @PathVariable("id") @Pattern(regexp = PUBLIC_ID_PATTERN, message = INVALID_ID) String id) {
        service.delete(id);
        return Result.ok();
    }

    /**
     * 向量检索。
     *
     * <p>检索含一次模型向量化，是阻塞调用 —— 走异步派发，不占 Tomcat 工作线程。
     */
    @PostMapping(value = "/{id}/search", consumes = MediaType.APPLICATION_JSON_VALUE)
    public DeferredResult<Result<List<SearchResult>>> search(
            @Valid @PathVariable("id") @Pattern(regexp = PUBLIC_ID_PATTERN, message = INVALID_ID) String id,
            @Valid @RequestBody SearchQuery query) {
        return searchDispatcher.search(id, query);
    }
}
