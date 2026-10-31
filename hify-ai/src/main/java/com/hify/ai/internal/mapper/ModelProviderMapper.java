package com.hify.ai.internal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.ai.internal.entity.ModelProviderDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 模型接入点的数据访问接口，仅在本模块 internal 内使用。 */
public interface ModelProviderMapper extends BaseMapper<ModelProviderDO> {

    /** 名称唯一键覆盖软删除行，不能使用自动附加 deleted=0 的查询检查占用。 */
    @Select("SELECT id FROM ai_model_provider WHERE name = #{name} LIMIT 1")
    Long findIdByName(@Param("name") String name);
}
