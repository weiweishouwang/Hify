package com.hify.mcp.internal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.mcp.internal.entity.McpServerDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** MCP 服务的数据访问接口。 */
public interface McpServerMapper extends BaseMapper<McpServerDO> {

    /**
     * 按名称查主键。
     *
     * <p>唯一键 {@code uk_name} 覆盖软删除行，检查占用时不能使用自动附加
     * {@code deleted = 0} 的查询。
     */
    @Select("SELECT id FROM mcp_server WHERE name = #{name} LIMIT 1")
    Long findIdByName(@Param("name") String name);
}
