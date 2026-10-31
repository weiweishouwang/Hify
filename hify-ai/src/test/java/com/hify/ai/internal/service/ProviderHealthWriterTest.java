package com.hify.ai.internal.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.hify.ai.internal.entity.ProviderHealthObservation;
import com.hify.ai.internal.mapper.ProviderHealthMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 离线检查 Mapper SQL 与调用分支；不声称已做真实 MySQL 并发验证。 */
class ProviderHealthWriterTest {
    ProviderHealthObservation observation(boolean success) {
        var time = LocalDateTime.of(2026, 10, 31, 12, 0);
        return new ProviderHealthObservation(1L, 2L, time, time.plusSeconds(1), 1000L,
                success, success ? null : "TIMEOUT", success ? null : "供应商请求超时");
    }

    @Test
    void successAndFailureUseIndependentAtomicStatements() {
        var mapper = mock(ProviderHealthMapper.class);
        var writer = new ProviderHealthWriter(mapper);
        var success = observation(true);
        writer.record(success);
        verify(mapper).initializeIfCurrent(success);
        verify(mapper).recordSuccess(success);
        var failure = observation(false);
        writer.record(failure);
        verify(mapper).recordFailure(failure, 3);
    }

    @Test
    void sqlGuardsEnabledVersionAndObservationOrdering() {
        var configuration = new MybatisConfiguration();
        configuration.addMapper(ProviderHealthMapper.class);
        String namespace = ProviderHealthMapper.class.getName() + ".";
        var success = configuration.getMappedStatement(namespace + "recordSuccess")
                .getBoundSql(Map.of("observation", observation(true))).getSql();
        var failure = configuration.getMappedStatement(namespace + "recordFailure")
                .getBoundSql(Map.of("observation", observation(false), "threshold", 3)).getSql();
        assertThat(success).contains("status = 'UP'", "fail_count = 0", "last_success_at", "error_message = NULL");
        assertThat(failure).contains("fail_count + 1", "'DOWN'", "'UNKNOWN'", "last_failure_at");
        assertThat(failure.indexOf("fail_count =")).isLessThan(failure.indexOf("status ="));
        assertThat(failure.indexOf("status =")).isLessThan(failure.indexOf("provider_config_version ="));
        for (String sql : new String[]{success, failure}) {
            assertThat(sql).contains("enabled = 1", "deleted = 0", "config_version", "last_observed_at <=")
                    .doesNotContain("UPDATE ai_model_provider SET", "JOIN", "${");
        }
        assertThat(configuration.getMappedStatement(namespace + "initializeIfCurrent")
                .getBoundSql(Map.of("observation", observation(true))).getSql()).contains("INSERT IGNORE", "SELECT");
    }
}
