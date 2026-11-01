package com.hify.knowledge.internal.controller;

import com.hify.knowledge.api.DocumentListQuery;
import com.hify.knowledge.api.DocumentListResp;
import com.hify.knowledge.api.DocumentSummaryResp;
import com.hify.knowledge.api.DocumentUpload;
import com.hify.knowledge.api.KnowledgeDocumentService;
import com.hify.shared.web.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 文档 HTTP 接入层。
 *
 * <p>上传用 multipart；其余接口走 JSON。控制器只做「参数绑定 → 调服务 → 封 Result」。
 */
@RestController
@RequestMapping(value = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
public class DocumentController {

    private static final String PUBLIC_ID_PATTERN = "[0-7][0-9A-HJKMNP-TV-Z]{25}";
    private static final String INVALID_ID = "id 必须是26位 ULID";

    private final KnowledgeDocumentService service;

    public DocumentController(KnowledgeDocumentService service) {
        this.service = service;
    }

    /** 上传文档。索引是异步的，返回时状态是 uploaded。 */
    @PostMapping(value = "/knowledge-bases/{kbId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<DocumentSummaryResp> upload(
            @Valid @PathVariable("kbId") @Pattern(regexp = PUBLIC_ID_PATTERN, message = INVALID_ID) String kbId,
            @RequestParam("file") MultipartFile file) throws IOException {
        String contentType = file.getContentType();
        String fileName = file.getOriginalFilename() == null ? "upload.txt" : file.getOriginalFilename();
        DocumentUpload upload = new DocumentUpload(fileName, contentType, file.getBytes());
        return Result.ok(service.upload(kbId, upload));
    }

    @GetMapping("/knowledge-bases/{kbId}/documents")
    public Result<DocumentListResp> list(
            @Valid @PathVariable("kbId") @Pattern(regexp = PUBLIC_ID_PATTERN, message = INVALID_ID) String kbId,
            @Valid @ModelAttribute DocumentListQuery query) {
        return Result.ok(service.list(kbId, query));
    }

    @GetMapping("/documents/{id}")
    public Result<DocumentSummaryResp> get(
            @Valid @PathVariable("id") @Pattern(regexp = PUBLIC_ID_PATTERN, message = INVALID_ID) String id) {
        return Result.ok(service.get(id));
    }

    @DeleteMapping("/documents/{id}")
    public Result<Void> delete(
            @Valid @PathVariable("id") @Pattern(regexp = PUBLIC_ID_PATTERN, message = INVALID_ID) String id) {
        service.delete(id);
        return Result.ok();
    }

    /** 重新索引：重新分块、向量化并覆盖 PG 里的分段。 */
    @PostMapping("/documents/{id}/reindex")
    public Result<DocumentSummaryResp> reindex(
            @Valid @PathVariable("id") @Pattern(regexp = PUBLIC_ID_PATTERN, message = INVALID_ID) String id) {
        return Result.ok(service.reindex(id));
    }
}
