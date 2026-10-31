package com.hify.tool.internal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.tool.internal.entity.ToolDefinitionDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 工具定义的数据访问接口，仅在本模块 internal 内使用。 */
public interface ToolDefinitionMapper extends BaseMapper<ToolDefinitionDO> {

    /**
     * 按来源与工具标识查主键。
     *
     * <p>唯一键 {@code uk_tool_key} 覆盖软删除行，检查占用时不能使用自动附加
     * {@code deleted = 0} 的查询，否则删掉的工具标识会被重复使用。
     */
    @Select("SELECT id FROM tool_definition WHERE source_type = #{sourceType} AND tool_key = #{toolKey} LIMIT 1")
    Long findIdByToolKey(@Param("sourceType") String sourceType, @Param("toolKey") String toolKey);

    /**
     * 按工具名查主键。
     *
     * <p>工具名没有数据库唯一键（内置与 MCP 的名字由代码 / 上游决定，加唯一键会与
     * 目录同步的 upsert 打架），唯一性由服务层保证。
     *
     * <p><b>只查未删除的行</b>：软删除的工具已经绑不到 Agent 上，不会造成
     * function calling 撞名，不该继续占着名字 —— 否则删掉一个 HTTP 工具后，
     * 同名的内置工具就再也同步不进来了。
     */
    @Select("SELECT id FROM tool_definition WHERE name = #{name} AND deleted = 0 LIMIT 1")
    Long findIdByName(@Param("name") String name);

    /** 某来源下所有未删除行的工具标识，用于算出哪些行本次没被发现、该下架。 */
    @Select("SELECT tool_key FROM tool_definition WHERE source_type = #{sourceType} AND deleted = 0")
    List<String> listToolKeysBySource(@Param("sourceType") String sourceType);
}
