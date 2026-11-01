package com.hify.knowledge.internal.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
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
class KnowledgeEntityMappingTest {

    private static final String ENTITY_PACKAGE = "com.hify.knowledge.internal.entity.";
    private static final String MAPPER_PACKAGE = "com.hify.knowledge.internal.mapper.";

    @ParameterizedTest
    @CsvSource({
            "KnowledgeBaseDO, kb_knowledge_base",
            "DocumentDO, kb_document"
    })
    @DisplayName("实体映射到 kb 表，字段均有 JavaBean 读写方法")
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
    @CsvSource({
            "KnowledgeBaseDO, KnowledgeBaseMapper",
            "DocumentDO, DocumentMapper"
    })
    @DisplayName("Mapper 继承对应实体的 BaseMapper")
    void mapperGeneric(String entityName, String mapperName) throws Exception {
        Class<?> mapper = Class.forName(MAPPER_PACKAGE + mapperName);
        assertThat(mapper.isInterface()).isTrue();
        assertThat(ResolvableType.forClass(mapper).as(BaseMapper.class).getGeneric(0).resolve())
                .isEqualTo(loadEntity(entityName));
    }

    @ParameterizedTest
    @CsvSource({"KnowledgeBaseDO", "DocumentDO"})
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
    }

    @Test
    @DisplayName("知识库保留分块参数、向量维度与配置版本")
    void knowledgeBaseColumns() throws Exception {
        assertThat(tableInfo(loadEntity("KnowledgeBaseDO")).getFieldList())
                .extracting(TableFieldInfo::getColumn)
                .contains("public_id", "name", "description", "embedding_model", "embedding_dim",
                        "chunk_size", "chunk_overlap", "doc_count", "status", "config_version");
    }

    @Test
    @DisplayName("文档保留状态机字段与原文列")
    void documentColumns() throws Exception {
        assertThat(tableInfo(loadEntity("DocumentDO")).getFieldList())
                .extracting(TableFieldInfo::getColumn)
                .contains("public_id", "kb_id", "name", "file_type", "file_size", "content_hash",
                        "content", "status", "chunk_count", "error_message", "indexed_at");
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
