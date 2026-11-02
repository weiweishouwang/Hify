package com.hify.workflow.internal.entity;

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
import org.springframework.core.ResolvableType;

import java.beans.Introspector;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/** 不连数据库，校验实体、Mapper 泛型与列映射。 */
class WorkflowEntityMappingTest {

    private static final String ENTITY_PACKAGE = "com.hify.workflow.internal.entity.";
    private static final String MAPPER_PACKAGE = "com.hify.workflow.internal.mapper.";

    @Test
    @DisplayName("定义映射到 wf_definition，是配置表 —— 有逻辑删除")
    void definitionIsConfigurationTable() throws Exception {
        Class<?> entity = loadEntity("WorkflowDefinitionDO");
        assertThat(entity.getSuperclass()).isEqualTo(BaseEntity.class);
        TableInfo table = tableInfo(entity);
        assertThat(table.getTableName()).isEqualTo("wf_definition");
        assertThat(table.getKeyColumn()).isEqualTo("id");
        assertThat(table.getIdType()).isEqualTo(IdType.AUTO);
        assertThat(table.isLogicDelete()).isTrue();
        assertThat(field(table, "createdAt").getFieldFill()).isEqualTo(FieldFill.INSERT);
        assertThat(table.getFieldList()).extracting(TableFieldInfo::getColumn)
                .contains("public_id", "name", "description", "definition", "version", "status");
    }

    @Test
    @DisplayName("运行记录是事件流表 —— 没有逻辑删除，复合主键")
    void runIsEventStreamTable() throws Exception {
        Class<?> entity = loadEntity("WorkflowRunDO");
        assertThat(entity.getSuperclass()).isNotEqualTo(BaseEntity.class);
        TableInfo table = tableInfo(entity);
        assertThat(table.getTableName()).isEqualTo("wf_run");
        assertThat(table.isLogicDelete()).isFalse();
        assertThat(table.getFieldList()).extracting(TableFieldInfo::getColumn)
                .doesNotContain("deleted", "created_by");
        assertThat(field(table, "createdAt").getFieldFill()).isEqualTo(FieldFill.INSERT);
    }

    @Test
    @DisplayName("JSON 列用 JacksonTypeHandler")
    void jsonColumns() throws Exception {
        assertThat(field(tableInfo(loadEntity("WorkflowDefinitionDO")), "definition").getTypeHandler())
                .isEqualTo(JacksonTypeHandler.class);
        assertThat(field(tableInfo(loadEntity("WorkflowDefinitionDO")), "definition").getColumn())
                .isEqualTo("definition");
        assertThat(field(tableInfo(loadEntity("WorkflowRunDO")), "input").getTypeHandler())
                .isEqualTo(JacksonTypeHandler.class);
        assertThat(field(tableInfo(loadEntity("WorkflowRunDO")), "output").getTypeHandler())
                .isEqualTo(JacksonTypeHandler.class);
    }

    @Test
    @DisplayName("Mapper 继承对应实体的 BaseMapper")
    void mapperGeneric() throws Exception {
        assertGeneric("WorkflowMapper", "WorkflowDefinitionDO");
        assertGeneric("WorkflowRunMapper", "WorkflowRunDO");
    }

    private static void assertGeneric(String mapperName, String entityName) throws Exception {
        Class<?> mapper = Class.forName(MAPPER_PACKAGE + mapperName);
        assertThat(mapper.isInterface()).isTrue();
        assertThat(ResolvableType.forClass(mapper).as(BaseMapper.class).getGeneric(0).resolve())
                .isEqualTo(loadEntity(entityName));
    }

    @Test
    @DisplayName("字段均有 JavaBean 读写方法")
    void accessors() throws Exception {
        for (String name : new String[] {"WorkflowDefinitionDO", "WorkflowRunDO"}) {
            Class<?> entity = loadEntity(name);
            TableInfo table = tableInfo(entity);
            var properties = Arrays.asList(Introspector.getBeanInfo(entity).getPropertyDescriptors());
            for (TableFieldInfo field : table.getFieldList()) {
                var property = properties.stream()
                        .filter(item -> item.getName().equals(field.getProperty()))
                        .findFirst().orElseThrow();
                assertThat(property.getReadMethod()).isNotNull();
                assertThat(property.getWriteMethod()).isNotNull();
            }
        }
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
