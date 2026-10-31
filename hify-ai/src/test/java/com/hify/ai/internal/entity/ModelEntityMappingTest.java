package com.hify.ai.internal.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.hify.shared.mybatis.BaseEntity;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.ResolvableType;

import java.beans.Introspector;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 不连数据库，校验实体、Mapper 泛型与 JSON 结果映射。 */
class ModelEntityMappingTest {

    private static final String ENTITY_PACKAGE = "com.hify.ai.internal.entity.";
    private static final String MAPPER_PACKAGE = "com.hify.ai.internal.mapper.";

    @ParameterizedTest
    @CsvSource({
            "ModelProviderDO, ai_model_provider",
            "ModelDO, ai_model",
            "ProviderHealthDO, ai_model_provider_health"
    })
    @DisplayName("实体映射到 ai 表，字段均有 JavaBean 读写方法")
    void tableAndAccessors(String entityName, String tableName) throws Exception {
        Class<?> entity = loadEntity(entityName);
        TableInfo table = tableInfo(entity);
        assertThat(table.getTableName()).isEqualTo(tableName);
        var properties = Arrays.asList(Introspector.getBeanInfo(entity).getPropertyDescriptors());
        for (TableFieldInfo field : table.getFieldList()) {
            var property = properties.stream()
                    .filter(item -> item.getName().equals(field.getProperty()))
                    .findFirst().orElseThrow();
            assertThat(property.getReadMethod()).isNotNull();
            assertThat(property.getWriteMethod()).isNotNull();
        }
    }

    @ParameterizedTest
    @CsvSource({"ModelProviderDO, ModelProviderMapper", "ModelDO, ModelMapper",
            "ProviderHealthDO, ProviderHealthMapper"})
    @DisplayName("Mapper 继承对应实体的 BaseMapper")
    void mapperGeneric(String entityName, String mapperName) throws Exception {
        Class<?> mapper = Class.forName(MAPPER_PACKAGE + mapperName);
        assertThat(mapper.isInterface()).isTrue();
        assertThat(ResolvableType.forClass(mapper).as(BaseMapper.class).getGeneric(0).resolve())
                .isEqualTo(loadEntity(entityName));
    }

    @ParameterizedTest
    @CsvSource({"ModelProviderDO", "ModelDO"})
    @DisplayName("配置实体继承 BaseEntity，使用自增主键、审计与逻辑删除")
    void configurationEntityBase(String entityName) throws Exception {
        Class<?> entity = loadEntity(entityName);
        assertThat(entity.getSuperclass()).isEqualTo(BaseEntity.class);
        TableInfo table = tableInfo(entity);
        assertThat(table.getKeyColumn()).isEqualTo("id");
        assertThat(table.getIdType()).isEqualTo(IdType.AUTO);
        assertThat(table.isLogicDelete()).isTrue();
        assertThat(field(table, "createdAt").getFieldFill()).isEqualTo(FieldFill.INSERT);
        assertThat(field(table, "updatedAt").getFieldFill()).isEqualTo(FieldFill.INSERT_UPDATE);
        assertThat(field(table, "createdBy").getColumn()).isEqualTo("created_by");
    }

    @Test
    @DisplayName("健康实体以 provider_id 为输入主键，不携带配置表的审计与逻辑删除字段")
    void healthEntityOwnFields() throws Exception {
        Class<?> entity = loadEntity("ProviderHealthDO");
        assertThat(BaseEntity.class.isAssignableFrom(entity)).isFalse();
        TableInfo table = tableInfo(entity);
        assertThat(table.getKeyColumn()).isEqualTo("provider_id");
        assertThat(table.getKeyProperty()).isEqualTo("providerId");
        assertThat(table.getIdType()).isEqualTo(IdType.INPUT);
        assertThat(table.isLogicDelete()).isFalse();
        assertThat(table.getFieldList()).extracting(TableFieldInfo::getColumn)
                .contains("status", "fail_count", "latency_ms", "last_success_at", "last_failure_at",
                        "error_code", "error_message", "last_observed_at", "observation_source",
                        "observed_model_id", "provider_config_version", "updated_at")
                .doesNotContain("id", "public_id", "deleted", "created_by", "created_at");
        assertThat(field(table, "updatedAt").getFieldFill()).isEqualTo(FieldFill.INSERT_UPDATE);
    }

    @ParameterizedTest
    @CsvSource({
            "ModelProviderDO, authConfig, auth_config",
            "ModelProviderDO, extraParams, extra_params",
            "ModelDO, capabilities, capabilities",
            "ModelDO, defaultParams, default_params",
            "ModelDO, extraParams, extra_params"
    })
    @DisplayName("JSON 字段的读写均使用 JacksonTypeHandler，保留嵌套结构")
    void jsonResultMap(String entityName, String property, String column) throws Exception {
        Class<?> entity = loadEntity(entityName);
        TableInfo table = tableInfo(entity);
        TableFieldInfo field = field(table, property);
        assertThat(field.getColumn()).isEqualTo(column);
        assertThat(field.getTypeHandler()).isEqualTo(JacksonTypeHandler.class);
        assertThat(table.isAutoInitResultMap()).isTrue();
        var mapping = table.getConfiguration().getResultMap(table.getResultMap()).getResultMappings()
                .stream().filter(item -> property.equals(item.getProperty())).findFirst().orElseThrow();
        assertThat(mapping.getTypeHandler()).isInstanceOf(JacksonTypeHandler.class);
        JacksonTypeHandler handler = (JacksonTypeHandler) mapping.getTypeHandler();
        Map<String, Object> value = Map.of("enabled", true, "limit", 42,
                "nested", Map.of("names", List.of("first", "second")));
        assertThat(handler.parse(handler.toJson(value))).isEqualTo(value);
        assertThat(handler.parse("{}")).isEqualTo(Map.of());
        assertThat(handler.parse("null")).isNull();
    }

    @Test
    @DisplayName("供应商与模型保留 public_id、模型关联和配置版本")
    void configurationColumns() throws Exception {
        assertThat(tableInfo(loadEntity("ModelProviderDO")).getFieldList())
                .extracting(TableFieldInfo::getColumn)
                .contains("public_id", "name", "type", "base_url", "auth_config", "extra_params",
                        "enabled", "config_version")
                .doesNotContain("api_key", "api_key_encrypted", "health_status", "fail_count");
        assertThat(tableInfo(loadEntity("ModelDO")).getFieldList())
                .extracting(TableFieldInfo::getColumn)
                .contains("public_id", "provider_id", "model_name", "display_name", "model_type",
                        "context_window", "max_output_tokens", "embedding_dimension", "default_params",
                        "extra_params", "enabled", "sort_order", "config_version");
    }

    private static Class<?> loadEntity(String name) {
        // 反射加载允许在实体尚未创建时先运行测试，失败表现为缺少映射实体。
        try {
            return Class.forName(ENTITY_PACKAGE + name);
        } catch (ClassNotFoundException exception) {
            throw new AssertionError("缺少映射实体：" + name, exception);
        }
    }

    private static TableInfo tableInfo(Class<?> entity) {
        TableInfo table = TableInfoHelper.getTableInfo(entity);
        if (table == null) {
            var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
            assistant.setCurrentNamespace(entity.getName());
            table = TableInfoHelper.initTableInfo(assistant, entity);
        }
        return table;
    }

    private static TableFieldInfo field(TableInfo table, String property) {
        return table.getFieldList().stream().filter(item -> property.equals(item.getProperty()))
                .findFirst().orElseThrow(() -> new AssertionError("缺少字段：" + property));
    }
}
