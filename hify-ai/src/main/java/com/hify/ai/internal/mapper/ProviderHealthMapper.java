package com.hify.ai.internal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.ai.internal.entity.ProviderHealthDO;
import com.hify.ai.internal.entity.ProviderHealthObservation;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/** 健康观测只写独立快照表；配置版本与开始时间共同阻止旧结果覆盖。 */
public interface ProviderHealthMapper extends BaseMapper<ProviderHealthDO> {

    @Insert("""
            INSERT IGNORE INTO ai_model_provider_health (provider_id, status, fail_count)
            SELECT id, 'UNKNOWN', 0 FROM ai_model_provider
            WHERE id = #{observation.providerId} AND enabled = 1 AND deleted = 0
              AND config_version = #{observation.configVersion}
            """)
    int initializeIfCurrent(@Param("observation") ProviderHealthObservation observation);

    @Update("""
            UPDATE ai_model_provider_health
            SET status = 'UP', fail_count = 0,
                latency_ms = #{observation.latencyMs}, last_success_at = #{observation.finishedAt},
                error_code = NULL, error_message = NULL,
                last_observed_at = #{observation.startedAt}, observation_source = 'probe', observed_model_id = NULL,
                provider_config_version = #{observation.configVersion}, updated_at = #{observation.finishedAt}
            WHERE provider_id = #{observation.providerId}
              AND (last_observed_at IS NULL OR last_observed_at <= #{observation.startedAt})
              AND EXISTS (SELECT 1 FROM ai_model_provider p
                          WHERE p.id = #{observation.providerId} AND p.enabled = 1 AND p.deleted = 0
                            AND p.config_version = #{observation.configVersion})
            """)
    int recordSuccess(@Param("observation") ProviderHealthObservation observation);

    /**
     * MySQL 单表 UPDATE 的赋值从左至右：先更新计数，再用新计数判阈值，最后写配置版本。
     * 不改为多表 UPDATE/JOIN，否则不能依赖这一赋值顺序。
     */
    @Update("""
            UPDATE ai_model_provider_health
            SET fail_count = CASE WHEN provider_config_version <=> #{observation.configVersion}
                                  THEN LEAST(fail_count + 1, 2147483647) ELSE 1 END,
                status = CASE WHEN fail_count >= #{threshold} THEN 'DOWN'
                              WHEN NOT (provider_config_version <=> #{observation.configVersion}) THEN 'UNKNOWN'
                              ELSE status END,
                latency_ms = #{observation.latencyMs}, last_failure_at = #{observation.finishedAt},
                error_code = #{observation.errorCode}, error_message = #{observation.errorMessage},
                last_observed_at = #{observation.startedAt}, observation_source = 'probe', observed_model_id = NULL,
                provider_config_version = #{observation.configVersion}, updated_at = #{observation.finishedAt}
            WHERE provider_id = #{observation.providerId}
              AND (last_observed_at IS NULL OR last_observed_at <= #{observation.startedAt})
              AND EXISTS (SELECT 1 FROM ai_model_provider p
                          WHERE p.id = #{observation.providerId} AND p.enabled = 1 AND p.deleted = 0
                            AND p.config_version = #{observation.configVersion})
            """)
    int recordFailure(@Param("observation") ProviderHealthObservation observation, @Param("threshold") int threshold);
}
