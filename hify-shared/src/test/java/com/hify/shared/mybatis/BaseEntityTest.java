package com.hify.shared.mybatis;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** {@link BaseEntity} 的单元测试：字段与注解必须与建表 DDL 对得上。 */
class BaseEntityTest {

    @Test
    @DisplayName("id 是自增主键")
    void idIsAutoIncrementPrimaryKey() throws Exception {
        Field field = BaseEntity.class.getDeclaredField("id");
        assertThat(field.getType()).isEqualTo(Long.class);
        assertThat(field.getAnnotation(TableId.class)).isNotNull();
        assertThat(field.getAnnotation(TableId.class).type()).isEqualTo(IdType.AUTO);
    }

    @Test
    @DisplayName("createdAt 插入时自动填充，updatedAt 插入与更新都自动填充")
    void timeFieldsAreAutoFilled() throws Exception {
        Field createdAt = BaseEntity.class.getDeclaredField("createdAt");
        assertThat(createdAt.getType()).isEqualTo(LocalDateTime.class);
        assertThat(createdAt.getAnnotation(TableField.class).fill()).isEqualTo(FieldFill.INSERT);

        Field updatedAt = BaseEntity.class.getDeclaredField("updatedAt");
        assertThat(updatedAt.getType()).isEqualTo(LocalDateTime.class);
        assertThat(updatedAt.getAnnotation(TableField.class).fill()).isEqualTo(FieldFill.INSERT_UPDATE);
    }

    @Test
    @DisplayName("deleted 是逻辑删除标记且初值为 0")
    void deletedIsLogicDeleteWithZeroDefault() throws Exception {
        Field field = BaseEntity.class.getDeclaredField("deleted");
        assertThat(field.getType()).isEqualTo(Integer.class);
        assertThat(field.getAnnotation(TableLogic.class)).isNotNull();
        // 库里这一列是 NOT NULL DEFAULT 0，显式插 NULL 会直接报错，所以初值必须是 0
        assertThat(new BaseEntity().getDeleted()).isZero();
    }

    @Test
    @DisplayName("createdBy 插入时自动填充")
    void createdByIsFilledOnInsert() throws Exception {
        Field field = BaseEntity.class.getDeclaredField("createdBy");
        assertThat(field.getType()).isEqualTo(Long.class);
        assertThat(field.getAnnotation(TableField.class).fill()).isEqualTo(FieldFill.INSERT);
    }

    @Test
    @DisplayName("BaseDO 不再自带字段，继承 BaseEntity 即可")
    void baseDoInheritsEverything() {
        assertThat(BaseDO.class.getSuperclass()).isEqualTo(BaseEntity.class);
        // BaseDO 自己不再声明任何字段，避免与基类重复
        assertThat(BaseDO.class.getDeclaredFields()).isEmpty();
    }
}
