package com.hify.conversation.internal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.conversation.internal.entity.CitationDO;

/**
 * 消息引用的数据访问接口。
 *
 * <p><b>不要用 {@code deleteById}</b> —— 事件流表，没有 {@code deleted} 列。
 */
public interface CitationMapper extends BaseMapper<CitationDO> {
}
