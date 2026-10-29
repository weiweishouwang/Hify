package com.hify.shared.mybatis;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.hify.shared.context.CurrentUser;
import com.hify.shared.context.CurrentUserHolder;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/** {@link AuditMetaObjectHandler} 的单元测试。 */
class AuditMetaObjectHandlerTest {

    @TableName("ai_test_entity")
    static class TestEntity extends BaseDO {
        @TableField(fill = FieldFill.INSERT)
        private String name;

        String getName() {
            return name;
        }
    }

    private final AuditMetaObjectHandler handler = new AuditMetaObjectHandler();

    @BeforeAll
    static void initTableInfo() {
        // MyBatis-Plus 的自动填充依赖实体的 TableInfo 元数据，脱离 Spring 时需要手工初始化
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "test"), TestEntity.class);
    }

    @AfterEach
    void tearDown() {
        CurrentUserHolder.clear();
    }

    @Test
    @DisplayName("插入时填充创建时间与更新时间")
    void 插入填充时间() {
        TestEntity entity = new TestEntity();
        MetaObject metaObject = SystemMetaObject.forObject(entity);

        handler.insertFill(metaObject);

        assertThat(entity.getCreatedAt()).isNotNull();
        assertThat(entity.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("填充的时间是 UTC")
    void 填充UTC时间() {
        LocalDateTime before = LocalDateTime.now(ZoneOffset.UTC);
        TestEntity entity = new TestEntity();

        handler.insertFill(SystemMetaObject.forObject(entity));
        LocalDateTime after = LocalDateTime.now(ZoneOffset.UTC);

        assertThat(entity.getCreatedAt()).isBetween(before, after);
        assertThat(entity.getUpdatedAt()).isBetween(before, after);
    }

    @Test
    @DisplayName("有登录上下文时填充创建者")
    void 填充创建者() {
        CurrentUserHolder.set(CurrentUser.ofUser(42L, "张三", false));
        TestEntity entity = new TestEntity();

        handler.insertFill(SystemMetaObject.forObject(entity));

        assertThat(entity.getCreatedBy()).isEqualTo(42L);
    }

    @Test
    @DisplayName("没有登录上下文时创建者留空")
    void 无上下文创建者留空() {
        TestEntity entity = new TestEntity();

        handler.insertFill(SystemMetaObject.forObject(entity));

        assertThat(entity.getCreatedBy()).isNull();
    }

    @Test
    @DisplayName("更新时刷新更新时间，即使原值非空")
    void 更新刷新更新时间() {
        CurrentUserHolder.set(CurrentUser.ofUser(42L, "张三", false));
        TestEntity entity = new TestEntity();
        handler.insertFill(SystemMetaObject.forObject(entity));

        // 模拟从数据库读出的实体：updatedAt 已有值
        LocalDateTime stale = LocalDateTime.now(ZoneOffset.UTC).minusHours(1);
        entity.setUpdatedAt(stale);

        handler.updateFill(SystemMetaObject.forObject(entity));

        // 必须被刷新成当前时间，而不是保持旧值
        assertThat(entity.getUpdatedAt()).isAfter(stale);
    }

    @Test
    @DisplayName("更新时不动创建时间与创建者")
    void 更新不动创建字段() {
        CurrentUserHolder.set(CurrentUser.ofUser(42L, "张三", false));
        TestEntity entity = new TestEntity();
        handler.insertFill(SystemMetaObject.forObject(entity));

        LocalDateTime createdAt = entity.getCreatedAt();
        Long createdBy = entity.getCreatedBy();

        handler.updateFill(SystemMetaObject.forObject(entity));

        assertThat(entity.getCreatedAt()).isEqualTo(createdAt);
        assertThat(entity.getCreatedBy()).isEqualTo(createdBy);
    }

    @Test
    @DisplayName("已有值时不覆盖（strictFill 语义）")
    void 不覆盖已有值() {
        TestEntity entity = new TestEntity();
        LocalDateTime preset = LocalDateTime.now(ZoneOffset.UTC).minusDays(3);
        entity.setCreatedAt(preset);

        handler.insertFill(SystemMetaObject.forObject(entity));

        assertThat(entity.getCreatedAt()).isEqualTo(preset);
    }
}
