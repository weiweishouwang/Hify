package com.hify.ai.internal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.ai.internal.entity.ModelDO;
import com.hify.ai.internal.entity.ProviderModelCount;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 接入点下模型的数据访问接口。 */
public interface ModelMapper extends BaseMapper<ModelDO> {

    /** 只统计当前页接入点的启用模型，不用于计算分页总数；调用方保证 ID 集合非空。 */
    @Select("""
            <script>
            SELECT provider_id, COUNT(id) AS enabled_model_count
            FROM ai_model
            WHERE enabled = 1 AND deleted = 0 AND provider_id IN
            <foreach collection="providerIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            GROUP BY provider_id
            </script>
            """)
    @ConstructorArgs({@Arg(column = "provider_id", javaType = Long.class),
            @Arg(column = "enabled_model_count", javaType = Long.class)})
    List<ProviderModelCount> countEnabledByProviderIds(@Param("providerIds") List<Long> providerIds);

    /**
     * 按接入点与上游模型标识查主键。
     *
     * <p>唯一键 {@code uk_provider_model} 覆盖软删除行，检查占用时不能使用自动附加
     * {@code deleted = 0} 的查询。
     */
    @Select("SELECT id FROM ai_model WHERE provider_id = #{providerId} AND model_name = #{modelName} LIMIT 1")
    Long findIdByProviderAndModel(@Param("providerId") Long providerId, @Param("modelName") String modelName);

    /** 某接入点下所有未删除行的上游模型标识，用于算出哪些模型上游不再提供、该停用。 */
    @Select("SELECT model_name FROM ai_model WHERE provider_id = #{providerId} AND deleted = 0")
    List<String> listModelNamesByProvider(@Param("providerId") Long providerId);
}
