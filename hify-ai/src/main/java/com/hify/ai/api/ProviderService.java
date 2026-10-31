package com.hify.ai.api;

import java.util.List;

/** 接入点公开契约，只传 DTO；调用模型、工具或 MCP 不属于本服务。 */
public interface ProviderService {

    ProviderConfigResp create(ProviderCreateReq request);

    ProviderListResp list(ProviderListQuery query);

    ProviderDetailResp get(String publicId);

    ProviderConfigResp update(String publicId, ProviderUpdateReq request);

    void delete(String publicId);

    /** 只读端点连通性测试；阻塞调用，HTTP 入口应提交到 llmExecutor。 */
    ProviderConnectionTestResp testConnection(String publicId);

    /** 列出上游可用的模型标识，供管理台同步模型时参考。 */
    List<String> listUpstreamModels(String publicId);
}
