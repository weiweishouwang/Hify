package com.hify.knowledge.api;

import java.util.List;

/**
 * 文档的存取契约。
 *
 * <p><b>文档元数据与状态机在 MySQL，分段正文与向量在 PostgreSQL。</b>
 * 上传后索引是异步的：接口返回时文档状态是 {@code uploaded}，
 * 索引完成后变成 {@code ready}（或 {@code failed}）。
 */
public interface KnowledgeDocumentService {

    /**
     * 上传一个 TXT 文档到知识库。
     *
     * @param kbPublicId 知识库的对外 ID
     * @param upload     上传内容
     * @return 落库后的文档
     */
    DocumentSummaryResp upload(String kbPublicId, DocumentUpload upload);

    /**
     * 按游标分页列出知识库下的文档。
     *
     * @param kbPublicId 知识库的对外 ID
     * @param query      查询条件
     * @return 一页文档
     */
    DocumentListResp list(String kbPublicId, DocumentListQuery query);

    /**
     * 取文档详情。
     *
     * @param publicId 文档的对外 ID
     * @return 文档
     */
    DocumentSummaryResp get(String publicId);

    /**
     * 删除文档：先软删并置 deleting，异步清掉 PG 里的分段后再硬删 MySQL 行。
     *
     * @param publicId 文档的对外 ID
     */
    void delete(String publicId);

    /**
     * 重新索引：重新分块、向量化并覆盖 PG 里的分段。
     *
     * @param publicId 文档的对外 ID
     * @return 重新提交后的文档
     */
    DocumentSummaryResp reindex(String publicId);
}
