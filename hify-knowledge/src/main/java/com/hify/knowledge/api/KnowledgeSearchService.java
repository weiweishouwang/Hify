package com.hify.knowledge.api;

import java.util.List;

/**
 * 知识库检索的契约。
 *
 * <p>供编排层（conversation / workflow）做检索增强用。
 * 实现把查询文本向量化后在 PostgreSQL 里做 HNSW 余弦检索。
 */
public interface KnowledgeSearchService {

    /**
     * 在指定知识库里检索最相关的分段。
     *
     * @param kbPublicId 知识库的对外 ID
     * @param query      检索条件
     * @return 命中结果，按相似度从高到低
     */
    List<SearchResult> search(String kbPublicId, SearchQuery query);
}
