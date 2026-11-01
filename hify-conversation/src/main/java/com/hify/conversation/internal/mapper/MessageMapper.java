package com.hify.conversation.internal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.conversation.internal.entity.MessageDO;

/**
 * 消息的数据访问接口。
 *
 * <p><b>不要用 {@code deleteById}</b> —— 消息是事件流表，没有 {@code deleted} 列。
 */
public interface MessageMapper extends BaseMapper<MessageDO> {
}
