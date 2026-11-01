package com.hify.knowledge.api;

import java.util.List;

/**
 * 知识库的存取契约。
 *
 * <p>知识库配置在 MySQL，分段正文与向量在 PostgreSQL —— 两库之间没有分布式事务，
 * 本接口只负责配置这一侧。
 */
public interface KnowledgeBaseService {

    /**
     * 创建知识库。
     *
     * @param request 创建请求
     * @return 创建后的知识库
     */
    KnowledgeBaseSummaryResp create(KnowledgeBaseCreateReq request);

    /**
     * 按游标分页列出知识库。
     *
     * @param query 查询条件
     * @return 一页知识库
     */
    KnowledgeBaseListResp list(KnowledgeBaseListQuery query);

    /**
     * 取知识库详情。
     *
     * @param publicId 知识库的对外 ID
     * @return 知识库
     */
    KnowledgeBaseSummaryResp get(String publicId);

    /**
     * 更新知识库的名称、描述与状态。
     *
     * @param publicId 知识库的对外 ID
     * @param request  更新请求
     * @return 更新后的知识库
     */
    KnowledgeBaseSummaryResp update(String publicId, KnowledgeBaseUpdateReq request);

    /**
     * 删除知识库。库里有文档时拒绝。
     *
     * @param publicId 知识库的对外 ID
     */
    void delete(String publicId);

    /**
     * 列出可用的 embedding 模型选项，供建库时选择。
     *
     * @return embedding 模型选项
     */
    List<EmbeddingModelOption> listEmbeddingModels();
}
