package com.hify.agent.internal.entity;

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

import static org.assertj.core.api.Assertions.assertThat;

/** 不连数据库，校验实体、Mapper 泛型与列映射。 */
class AgentEntityMappingTest {

    private static final String ENTITY_PACKAGE = "com.hify.agent.internal.entity.";
    private static final String MAPPER_PACKAGE = "com.hify.agent.internal.mapper.";

    @Test
    @DisplayName("实体映射到 ai_agent，字段均有 JavaBean 读写方法")
    void tableAndAccessors() throws Exception {
        Class<?> entity = loadEntity("AgentDO");
        TableInfo table = tableInfo(entity);
        assertThat(table.getTableName()).isEqualTo("ai_agent");
        var properties = Arrays.asList(Introspector.getBeanInfo(entity).getPropertyDescriptors());
        for (TableFieldInfo field : table.getFieldList()) {
            var property = properties.stream()
                    .filter(item -> item.getName().equals(field.getProperty()))
                    .findFirst().orElseThrow();
            assertThat(property.getReadMethod()).isNotNull();
            assertThat(property.getWriteMethod()).isNotNull();
        }
    }

    @Test
    @DisplayName("Mapper 继承对应实体的 BaseMapper")
    void mapperGeneric() throws Exception {
        Class<?> mapper = Class.forName(MAPPER_PACKAGE + "AgentMapper");
        assertThat(mapper.isInterface()).isTrue();
        assertThat(ResolvableType.forClass(mapper).as(BaseMapper.class).getGeneric(0).resolve())
                .isEqualTo(loadEntity("AgentDO"));
    }

    @Test
    @DisplayName("配置实体继承 BaseEntity，使用自增主键、审计与逻辑删除")
    void configurationEntityBase() throws Exception {
        Class<?> entity = loadEntity("AgentDO");
        assertThat(entity.getSuperclass()).isEqualTo(BaseEntity.class);
        TableInfo table = tableInfo(entity);
        assertThat(table.getKeyColumn()).isEqualTo("id");
        assertThat(table.getIdType()).isEqualTo(IdType.AUTO);
        assertThat(table.isLogicDelete()).isTrue();
        assertThat(field(table, "createdAt").getFieldFill()).isEqualTo(FieldFill.INSERT);
        assertThat(field(table, "updatedAt").getFieldFill()).isEqualTo(FieldFill.INSERT_UPDATE);
    }

    @ParameterizedTest
    @CsvSource({
            "AgentDO, toolIds, tool_ids",
            "AgentDO, knowledgeIds, knowledge_ids",
            "AgentDO, paramConfig, param_config"
    })
    @DisplayName("JSON 字段的读写均使用 JacksonTypeHandler，保留嵌套结构")
    void jsonResultMap(String entityName, String property, String column) throws Exception {
        Class<?> entity = loadEntity(entityName);
        TableInfo table = tableInfo(entity);
        TableFieldInfo field = field(table, property);
        assertThat(field.getColumn()).isEqualTo(column);
        assertThat(field.getTypeHandler()).isEqualTo(JacksonTypeHandler.class);
        assertThat(table.isAutoInitResultMap()).isTrue();
    }

    @Test
    @DisplayName("保留模型、提示词、绑定项、参数区、状态与配置版本列")
    void configurationColumns() throws Exception {
        assertThat(tableInfo(loadEntity("AgentDO")).getFieldList())
                .extracting(TableFieldInfo::getColumn)
                .contains("public_id", "name", "description", "model_id", "prompt_template",
                        "tool_ids", "knowledge_ids", "param_config", "status", "app_type", "config_version");
    }

    private static Class<?> loadEntity(String name) {
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
