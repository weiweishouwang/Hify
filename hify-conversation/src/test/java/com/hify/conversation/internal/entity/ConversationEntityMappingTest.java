package com.hify.conversation.internal.entity;

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
import org.springframework.core.ResolvableType;

import java.beans.Introspector;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/** 不连数据库，校验实体、Mapper 泛型与列映射。 */
class ConversationEntityMappingTest {

    private static final String ENTITY_PACKAGE = "com.hify.conversation.internal.entity.";
    private static final String MAPPER_PACKAGE = "com.hify.conversation.internal.mapper.";

    @Test
    @DisplayName("会话映射到 conv_session，是配置表 —— 有逻辑删除")
    void sessionIsConfigurationTable() throws Exception {
        Class<?> entity = loadEntity("ConversationDO");
        assertThat(entity.getSuperclass()).isEqualTo(BaseEntity.class);
        TableInfo table = tableInfo(entity);
        assertThat(table.getTableName()).isEqualTo("conv_session");
        assertThat(table.getKeyColumn()).isEqualTo("id");
        assertThat(table.getIdType()).isEqualTo(IdType.AUTO);
        assertThat(table.isLogicDelete()).isTrue();
        assertThat(field(table, "createdAt").getFieldFill()).isEqualTo(FieldFill.INSERT);
        assertThat(table.getFieldList())
                .extracting(TableFieldInfo::getColumn)
                .contains("public_id", "user_id", "agent_id", "title", "message_count",
                        "last_message_at", "status");
    }

    @Test
    @DisplayName("事件流实体不继承 BaseEntity，也没有逻辑删除")
    void eventStreamEntitiesHaveNoLogicDelete() throws Exception {
        for (String name : new String[] {"MessageDO", "RunDO", "CitationDO"}) {
            Class<?> entity = loadEntity(name);
            assertThat(entity.getSuperclass()).isNotEqualTo(BaseEntity.class);
            TableInfo table = tableInfo(entity);
            assertThat(table.isLogicDelete()).isFalse();
            assertThat(table.getFieldList()).extracting(TableFieldInfo::getColumn)
                    .doesNotContain("deleted", "created_by");
        }
    }

    @Test
    @DisplayName("三张事件流表各自映射到自己的表")
    void eventStreamTableNames() throws Exception {
        assertThat(tableInfo(loadEntity("MessageDO")).getTableName()).isEqualTo("conv_message");
        assertThat(tableInfo(loadEntity("RunDO")).getTableName()).isEqualTo("conv_run");
        assertThat(tableInfo(loadEntity("CitationDO")).getTableName()).isEqualTo("conv_message_citation");
    }

    @Test
    @DisplayName("事件流表按 created_at 分区 —— 插入时必须由应用填时间")
    void eventStreamFillsCreatedAt() throws Exception {
        for (String name : new String[] {"MessageDO", "RunDO", "CitationDO"}) {
            TableInfo table = tableInfo(loadEntity(name));
            assertThat(field(table, "createdAt").getFieldFill()).isEqualTo(FieldFill.INSERT);
        }
    }

    @Test
    @DisplayName("消息与引用的关键列都在")
    void messageAndCitationColumns() throws Exception {
        assertThat(tableInfo(loadEntity("MessageDO")).getFieldList())
                .extracting(TableFieldInfo::getColumn)
                .contains("session_id", "role", "content", "content_type", "model_id",
                        "provider_id", "token_count", "finish_reason", "latency_ms", "created_at");
        assertThat(tableInfo(loadEntity("CitationDO")).getFieldList())
                .extracting(TableFieldInfo::getColumn)
                .contains("session_id", "message_id", "chunk_id", "doc_id", "chunk_index",
                        "kb_id", "quote_text", "score");
    }

    @Test
    @DisplayName("Mapper 继承对应实体的 BaseMapper")
    void mapperGeneric() throws Exception {
        assertGeneric("ConversationMapper", "ConversationDO");
        assertGeneric("MessageMapper", "MessageDO");
        assertGeneric("RunMapper", "RunDO");
        assertGeneric("CitationMapper", "CitationDO");
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
        for (String name : new String[] {"ConversationDO", "MessageDO", "RunDO", "CitationDO"}) {
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
