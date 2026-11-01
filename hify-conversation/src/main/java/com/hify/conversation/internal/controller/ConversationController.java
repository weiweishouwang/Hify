package com.hify.conversation.internal.controller;

import com.hify.conversation.api.ConversationCreateReq;
import com.hify.conversation.api.ConversationListQuery;
import com.hify.conversation.api.ConversationListResp;
import com.hify.conversation.api.ConversationMessageResp;
import com.hify.conversation.api.ConversationService;
import com.hify.conversation.api.ConversationSummaryResp;
import com.hify.conversation.api.ConversationUpdateReq;
import com.hify.conversation.api.SendMessageReq;
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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/** 会话的 HTTP 接入层：只绑定校验参数、调用服务、封装 Result。 */
@RestController
@RequestMapping(value = "/api/v1/conversations")
public class ConversationController {

    private static final String PUBLIC_ID_PATTERN = "[0-7][0-9A-HJKMNP-TV-Z]{25}";
    private static final String INVALID_ID = "id 必须是26位 ULID";

    /**
     * SSE 连接的存活上限。
     *
     * <p>一轮对话最多 6 次模型调用（5 轮工具 + 1 轮收尾），每次上游读超时 120 秒 ——
     * 最坏情况要几分钟。给 10 分钟。
     */
    private static final long SSE_TIMEOUT_MS = 600_000L;

    private final ConversationService service;
    private final ConversationStreamDispatcher dispatcher;

    public ConversationController(ConversationService service, ConversationStreamDispatcher dispatcher) {
        this.service = service;
        this.dispatcher = dispatcher;
    }

    /** 沿用服务的 cursor/size 游标分页。 */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Result<ConversationListResp> list(@Valid @ModelAttribute ConversationListQuery query) {
        return Result.ok(service.list(query));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Result<ConversationSummaryResp> create(@Valid @RequestBody ConversationCreateReq request) {
        return Result.ok(service.create(request));
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Result<ConversationSummaryResp> get(
            @Valid @PathVariable("id") @Pattern(regexp = PUBLIC_ID_PATTERN, message = INVALID_ID) String id) {
        return Result.ok(service.get(id));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Result<ConversationSummaryResp> update(
            @Valid @PathVariable("id") @Pattern(regexp = PUBLIC_ID_PATTERN, message = INVALID_ID) String id,
            @Valid @RequestBody ConversationUpdateReq request) {
        return Result.ok(service.update(id, request));
    }

    @DeleteMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Result<Void> delete(
            @Valid @PathVariable("id") @Pattern(regexp = PUBLIC_ID_PATTERN, message = INVALID_ID) String id) {
        service.delete(id);
        return Result.ok();
    }

    /** 会话的消息历史，按时间正序。 */
    @GetMapping(value = "/{id}/messages", produces = MediaType.APPLICATION_JSON_VALUE)
    public Result<List<ConversationMessageResp>> messages(
            @Valid @PathVariable("id") @Pattern(regexp = PUBLIC_ID_PATTERN, message = INVALID_ID) String id,
            @RequestParam(required = false) Integer limit) {
        return Result.ok(service.listMessages(id, limit));
    }

    /**
     * 发一条消息，流式返回。
     *
     * <p><b>必须声明 {@code produces = text/event-stream}</b> ——
     * Spring MVC 只有在内容类型协商到它时才走 SSE 分帧，否则会退化成
     * 逐元素 JSON 写出（CLAUDE.md §3.4）。
     */
    @PostMapping(value = "/{id}/messages", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter send(
            @Valid @PathVariable("id") @Pattern(regexp = PUBLIC_ID_PATTERN, message = INVALID_ID) String id,
            @Valid @RequestBody SendMessageReq request) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        dispatcher.stream(id, request, emitter);
        return emitter;
    }
}
