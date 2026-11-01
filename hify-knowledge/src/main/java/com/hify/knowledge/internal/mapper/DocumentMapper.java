package com.hify.knowledge.internal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.knowledge.internal.entity.DocumentDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 文档的数据访问接口（MySQL 侧）。 */
public interface DocumentMapper extends BaseMapper<DocumentDO> {

    /**
     * 物理删除一行。
     *
     * <p>删除流程的最后一步：PG 里的分段清干净之后，MySQL 这一行不留残留
     * （软删标记只用于「正在删」这个中间态）。
     */
    @Update("DELETE FROM kb_document WHERE id = #{id}")
    int hardDelete(@Param("id") Long id);

    /** 知识库下未删除的文档数。 */
    @Select("SELECT COUNT(*) FROM kb_document WHERE kb_id = #{kbId} AND deleted = 0")
    long countByKbId(@Param("kbId") Long kbId);

    /** 知识库下处于指定状态的文档数。 */
    @Select("SELECT COUNT(*) FROM kb_document WHERE kb_id = #{kbId} AND deleted = 0 AND status = #{status}")
    long countByKbIdAndStatus(@Param("kbId") Long kbId, @Param("status") String status);
}
