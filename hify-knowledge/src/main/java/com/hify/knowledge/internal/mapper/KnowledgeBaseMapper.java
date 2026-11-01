package com.hify.knowledge.internal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.knowledge.internal.entity.KnowledgeBaseDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 知识库的数据访问接口（MySQL 侧）。 */
public interface KnowledgeBaseMapper extends BaseMapper<KnowledgeBaseDO> {

    /**
     * 按名称查主键。
     *
     * <p>唯一键 {@code uk_name} 覆盖软删除行，检查占用时不能使用自动附加
     * {@code deleted = 0} 的查询。
     */
    @Select("SELECT id FROM kb_knowledge_base WHERE name = #{name} LIMIT 1")
    Long findIdByName(@Param("name") String name);
}
