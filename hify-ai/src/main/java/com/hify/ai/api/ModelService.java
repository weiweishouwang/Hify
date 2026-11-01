package com.hify.ai.api;

/**
 * 模型管理的公开契约。
 *
 * <p>模型行来自「从接入点同步上游目录」—— 上游目录接口不标模型用途，
 * {@code chat / embedding} 由用户在管理台人工认定。
 */
public interface ModelService {

    /**
     * 按游标分页列出模型。
     *
     * @param query 查询条件
     * @return 一页模型
     */
    ModelListResp list(ModelListQuery query);

    /**
     * 取模型详情。
     *
     * @param publicId 模型的对外 ID
     * @return 模型摘要
     */
    ModelSummaryResp get(String publicId);

    /**
     * 更新模型配置（展示名、用途、能力、启停等）。
     *
     * @param publicId 模型的对外 ID
     * @param request  更新请求
     * @return 更新后的模型摘要
     */
    ModelSummaryResp update(String publicId, ModelUpdateReq request);

    /**
     * 删除模型（逻辑删除）。
     *
     * @param publicId 模型的对外 ID
     */
    void delete(String publicId);

    /**
     * 从接入点同步上游模型目录。
     *
     * @param providerPublicId 接入点的对外 ID
     * @return 同步统计
     */
    ModelSyncResp sync(String providerPublicId);
}
