package com.hify.demo.internal.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.shared.mybatis.BaseEntity;

/**
 * 演示条目的数据对象。
 *
 * <p>只承载业务字段，主键与审计字段来自 {@link BaseEntity}。
 *
 * <p>实体只在 {@code internal} 里用，不得出现在 {@code api} 包或作为跨模块方法的
 * 参数——跨模块只传 DTO（CLAUDE.md §3.2，ArchUnit 会拦）。
 */
@TableName("demo_item")
public class DemoItemDO extends BaseEntity {

    /** 名称。 */
    @TableField("name")
    private String name;

    /** 状态：0 草稿 / 1 启用 / 2 停用。 */
    @TableField("status")
    private Integer status;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }
}
