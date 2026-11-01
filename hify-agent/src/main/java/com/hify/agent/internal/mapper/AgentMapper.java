package com.hify.agent.internal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.agent.internal.entity.AgentDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** Agent 配置的数据访问接口。 */
public interface AgentMapper extends BaseMapper<AgentDO> {

    /**
     * 按名称查主键。
     *
     * <p>唯一键 {@code uk_name} 覆盖软删除行，检查占用时不能使用自动附加
     * {@code deleted = 0} 的查询。
     */
    @Select("SELECT id FROM ai_agent WHERE name = #{name} LIMIT 1")
    Long findIdByName(@Param("name") String name);

    /**
     * 按对外 ID 查主键（含软删行）。
     *
     * <p>删除后仍要能说清「这个 Agent 曾经存在过」，否则报错会变成「不存在」，
     * 与「不存在」的真实情况混在一起。
     */
    @Select("SELECT id FROM ai_agent WHERE public_id = #{publicId} LIMIT 1")
    Long findIdByPublicId(@Param("publicId") String publicId);

    /**
     * 按模型主键查 Agent 的对外 ID。
     *
     * <p>模型被停用或删除时，管理台要能提示「哪些 Agent 用着它」——
     * 按主键查走 {@code idx_model_id}。
     */
    @Select("SELECT public_id FROM ai_agent WHERE model_id = #{modelId} AND deleted = 0")
    List<String> findPublicIdsByModelId(@Param("modelId") Long modelId);
}
