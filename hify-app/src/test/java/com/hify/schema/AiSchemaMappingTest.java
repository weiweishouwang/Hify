package com.hify.schema;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** 离线检查三张 AI 表的 DDL 与实体映射，不连接或修改数据库。 */
class AiSchemaMappingTest {

    private static String schema;

    @BeforeAll
    static void readSchema() throws Exception {
        try (var input = new ClassPathResource("db/schema.sql").getInputStream()) {
            schema = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @ParameterizedTest
    @CsvSource({
            "ai_model_provider, ModelProviderDO",
            "ai_model, ModelDO",
            "ai_model_provider_health, ProviderHealthDO"
    })
    @DisplayName("DDL 列与 MyBatis-Plus 实体映射完全一致，包含继承字段")
    void columnsMatchEntity(String tableName, String entityName) throws Exception {
        Class<?> entity = Class.forName("com.hify.ai.internal.entity." + entityName);
        TableInfo table = TableInfoHelper.getTableInfo(entity);
        if (table == null) {
            var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
            assistant.setCurrentNamespace(entity.getName());
            table = TableInfoHelper.initTableInfo(assistant, entity);
        }
        var columns = new LinkedHashSet<String>();
        columns.add(table.getKeyColumn());
        table.getFieldList().stream().map(TableFieldInfo::getColumn).forEach(columns::add);
        assertThat(columnDefinitions(tableDefinition(tableName)).keySet())
                .containsExactlyInAnyOrderElementsOf(columns);
    }

    @ParameterizedTest
    @CsvSource({
            "ai_model_provider, auth_config",
            "ai_model_provider, extra_params",
            "ai_model, capabilities",
            "ai_model, default_params",
            "ai_model, extra_params"
    })
    @DisplayName("TypeHandler 对应的列使用 MySQL JSON 类型")
    void jsonColumns(String tableName, String column) {
        assertThat(columnDefinitions(tableDefinition(tableName)).get(column)).startsWith("JSON ");
    }

    @Test
    @DisplayName("配置表保留唯一键与默认值，移除旧凭据及连接池配置列")
    void configurationConstraints() {
        String provider = tableDefinition("ai_model_provider");
        assertThat(provider).contains("UNIQUE KEY `uk_public_id` (`public_id`)",
                        "UNIQUE KEY `uk_name` (`name`)")
                .doesNotContain("api_key_encrypted", "max_connections", "timeout_config", "retry_config",
                        "breaker_config", "fallback_chain", "extra_config");
        assertThat(columnDefinitions(provider).get("config_version"))
                .startsWith("BIGINT UNSIGNED NOT NULL DEFAULT 1");
        String model = tableDefinition("ai_model");
        assertThat(model).contains("UNIQUE KEY `uk_provider_model` (`provider_id`, `model_name`)",
                "FOREIGN KEY (`provider_id`) REFERENCES `ai_model_provider` (`id`)");
        assertThat(columnDefinitions(model).get("model_name")).contains("COLLATE utf8mb4_0900_as_cs");
        assertThat(columnDefinitions(model).get("config_version"))
                .startsWith("BIGINT UNSIGNED NOT NULL DEFAULT 1");
    }

    @Test
    @DisplayName("健康表以 provider_id 为非自增主键，保留观测信息且无逻辑删除")
    void healthConstraints() {
        String health = tableDefinition("ai_model_provider_health");
        assertThat(health).contains("PRIMARY KEY (`provider_id`)",
                        "FOREIGN KEY (`provider_id`) REFERENCES `ai_model_provider` (`id`)")
                .doesNotContain("AUTO_INCREMENT", "ON DELETE CASCADE", "ON UPDATE CASCADE");
        Map<String, String> columns = columnDefinitions(health);
        assertThat(columns).doesNotContainKeys("id", "public_id", "deleted", "created_by", "created_at");
        assertThat(columns.get("status")).startsWith("VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN'");
        assertThat(columns.get("fail_count")).startsWith("INT UNSIGNED NOT NULL DEFAULT 0");
        assertThat(columns.get("latency_ms")).startsWith("BIGINT UNSIGNED NULL");
        assertThat(columns.get("last_success_at")).startsWith("DATETIME(3) NULL");
        assertThat(columns.get("updated_at")).contains("CURRENT_TIMESTAMP(3)");
    }

    private static String tableDefinition(String table) {
        var matcher = Pattern.compile("CREATE TABLE `" + Pattern.quote(table)
                + "` \\((.*?)\\) ENGINE=InnoDB", Pattern.DOTALL).matcher(schema);
        assertThat(matcher.find()).as("存在建表定义 %s", table).isTrue();
        return matcher.group(1);
    }

    private static Map<String, String> columnDefinitions(String definition) {
        var matcher = Pattern.compile("^\\s*`(\\w+)`\\s+(.+)$", Pattern.MULTILINE).matcher(definition);
        Map<String, String> columns = new LinkedHashMap<>();
        while (matcher.find()) {
            columns.put(matcher.group(1), matcher.group(2));
        }
        return columns;
    }
}
