package com.hify.ai.internal.mapper;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ModelCountMappingTest {
    @Test
    void enabledCountIsGroupedAndRestrictedToCurrentPageIds() {
        var config = new MybatisConfiguration();
        config.addMapper(ModelMapper.class);
        var statement = config.getMappedStatement(ModelMapper.class.getName() + ".countEnabledByProviderIds");
        var bound = statement.getBoundSql(Map.of("providerIds", List.of(1L, 2L)));
        assertThat(bound.getSql()).contains("COUNT(id)", "enabled = 1", "deleted = 0", "GROUP BY provider_id")
                .doesNotContain("${", "OFFSET");
        assertThat(bound.getParameterMappings()).hasSize(2);
        assertThat(statement.getResultMaps().getFirst().getConstructorResultMappings()).hasSize(2);
    }
}
