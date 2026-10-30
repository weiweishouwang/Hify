package com.hify.shared.mybatis;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link BaseEntity} 的 MyBatis-Plus 映射校验。
 *
 * <p>不连数据库，直接检查 MyBatis-Plus 解析出来的 {@link TableInfo}——
 * 字段映射、逻辑删除标记、INSERT 列清单都能静态确认。
 */
class BaseEntityMappingTest {

    /** 探针实体：继承 BaseEntity，模拟一张真实业务表。 */
    @TableName("probe_entity")
    static class ProbeDO extends BaseEntity {

        @TableField("name")
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    private static TableInfo tableInfo;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace(ProbeDO.class.getName());
        TableInfoHelper.initTableInfo(assistant, ProbeDO.class);
        tableInfo = TableInfoHelper.getTableInfo(ProbeDO.class);
    }

    @Test
    @DisplayName("主键是 id 且自增")
    void primaryKey() {
        assertThat(tableInfo.getKeyColumn()).isEqualTo("id");
        assertThat(tableInfo.getKeyProperty()).isEqualTo("id");
        assertThat(tableInfo.getIdType()).isEqualTo(IdType.AUTO);
    }

    @Test
    @DisplayName("审计字段映射到下划线列名")
    void auditFieldColumns() {
        assertThat(fieldInfo("createdAt").getColumn()).isEqualTo("created_at");
        assertThat(fieldInfo("updatedAt").getColumn()).isEqualTo("updated_at");
        assertThat(fieldInfo("createdBy").getColumn()).isEqualTo("created_by");
    }

    @Test
    @DisplayName("deleted 被识别为逻辑删除字段，删除值 1、未删除值 0")
    void deletedIsLogicDeleteField() {
        assertThat(tableInfo.isLogicDelete()).isTrue();
        TableFieldInfo logicField = tableInfo.getLogicDeleteFieldInfo();
        assertThat(logicField).isNotNull();
        assertThat(logicField.getProperty()).isEqualTo("deleted");
        assertThat(logicField.getColumn()).isEqualTo("deleted");
        assertThat(logicField.getLogicDeleteValue()).isEqualTo("1");
        assertThat(logicField.getLogicNotDeleteValue()).isEqualTo("0");
    }

    @Test
    @DisplayName("deleted 参与 INSERT，且实体初值是 0")
    void deletedIsIncludedInInsert() {
        // MP 插入时只在值非空才写这一列：`<if test="deleted != null">deleted,</if>`
        String insertColumns = tableInfo.getAllInsertSqlColumnMaybeIf(null, false);
        assertThat(insertColumns).contains("deleted");
        // 初值显式给 0，不依赖建表语句里的 DEFAULT 0
        assertThat(new ProbeDO().getDeleted()).isZero();
    }

    @Test
    @DisplayName("自动填充标记被识别")
    void fillStrategies() {
        assertThat(fieldInfo("createdAt").getFieldFill())
                .isEqualTo(com.baomidou.mybatisplus.annotation.FieldFill.INSERT);
        assertThat(fieldInfo("updatedAt").getFieldFill())
                .isEqualTo(com.baomidou.mybatisplus.annotation.FieldFill.INSERT_UPDATE);
        assertThat(fieldInfo("createdBy").getFieldFill())
                .isEqualTo(com.baomidou.mybatisplus.annotation.FieldFill.INSERT);
    }

    private static TableFieldInfo fieldInfo(String property) {
        return tableInfo.getFieldList().stream()
                .filter(item -> property.equals(item.getProperty()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("找不到字段：" + property));
    }
}
