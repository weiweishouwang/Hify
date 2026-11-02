package com.hify.workflow.internal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.workflow.internal.entity.WorkflowRunDO;

/**
 * 工作流运行记录的数据访问接口。
 *
 * <p><b>不要用 {@code deleteById}</b> —— 事件流表，没有 {@code deleted} 列。
 */
public interface WorkflowRunMapper extends BaseMapper<WorkflowRunDO> {
}
