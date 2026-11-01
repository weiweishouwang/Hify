package com.hify.conversation.api;

import java.util.List;

/**
 * 会话的存取契约。
 *
 * <p><b>流式对话不在本接口里</b> —— 它是一条 SSE 长连接，不是普通的请求响应；
 * 走 {@code ConversationController} 的专用端点。
 */
public interface ConversationService {

    /**
     * 创建会话。
     *
     * @param request 创建请求
     * @return 创建后的会话
     */
    ConversationSummaryResp create(ConversationCreateReq request);

    /**
     * 按游标分页列出当前用户的会话。
     *
     * @param query 查询条件
     * @return 一页会话
     */
    ConversationListResp list(ConversationListQuery query);

    /**
     * 取会话详情。
     *
     * @param publicId 会话的对外 ID
     * @return 会话
     */
    ConversationSummaryResp get(String publicId);

    /**
     * 更新会话（改标题、归档）。
     *
     * @param publicId 会话的对外 ID
     * @param request  更新请求
     * @return 更新后的会话
     */
    ConversationSummaryResp update(String publicId, ConversationUpdateReq request);

    /**
     * 删除会话（逻辑删除）。消息与运行记录保留 —— 它们是事件流。
     *
     * @param publicId 会话的对外 ID
     */
    void delete(String publicId);

    /**
     * 列出会话的消息，按时间正序。
     *
     * @param publicId 会话的对外 ID
     * @param limit    最多返回多少条；为空时取默认值
     * @return 消息列表
     */
    List<ConversationMessageResp> listMessages(String publicId, Integer limit);
}
