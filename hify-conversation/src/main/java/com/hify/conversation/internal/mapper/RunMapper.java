package com.hify.conversation.internal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.conversation.internal.entity.RunDO;

/**
 * 运行记录的数据访问接口。
 *
 * <p><b>不要用 {@code deleteById}</b> —— 事件流表，没有 {@code deleted} 列。
 */
public interface RunMapper extends BaseMapper<RunDO> {
}
