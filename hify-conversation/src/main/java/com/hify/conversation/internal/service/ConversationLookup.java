package com.hify.conversation.internal.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hify.conversation.internal.entity.ConversationDO;
import com.hify.conversation.internal.mapper.ConversationMapper;
import com.hify.shared.api.CurrentUserService;
import com.hify.shared.exception.BizException;
import com.hify.shared.exception.ErrorCode;
import org.springframework.stereotype.Service;

/**
 * 会话查找与归属校验。
 *
 * <p><b>不属于当前用户时按「不存在」处理</b> —— 告诉调用者「这个会话存在但不是你的」
 * 等于泄露了别人的会话 id。
 */
@Service
public class ConversationLookup {

    private final ConversationMapper conversations;
    private final CurrentUserService currentUsers;

    public ConversationLookup(ConversationMapper conversations, CurrentUserService currentUsers) {
        this.conversations = conversations;
        this.currentUsers = currentUsers;
    }

    /**
     * 取当前用户的会话。
     *
     * @param publicId 会话的对外 ID
     * @return 会话行
     * @throws BizException 会话不存在或不属于当前用户
     */
    public ConversationDO requireOwned(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            throw ConversationSupport.invalid("会话标识不能为空");
        }
        ConversationDO entity = conversations.selectOne(new QueryWrapper<ConversationDO>()
                .eq("public_id", publicId.trim())
                .eq("user_id", currentUsers.currentUserId()));
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "会话不存在");
        }
        return entity;
    }
}
