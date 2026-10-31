package com.hify.tool.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 工具列表的查询条件：游标分页 + 来源 / 启停 / 关键字筛选。 */
public class ToolListQuery {

    /** 游标，取上一页返回的 nextCursor。 */
    @Size(max = 512, message = "游标过长")
    private String cursor;

    /** 每页条数。 */
    @Min(value = 1, message = "每页条数不能小于 1")
    @Max(value = 200, message = "每页条数不能大于 200")
    private Integer size;

    /** 按来源筛选：builtin / http / mcp。 */
    @Pattern(regexp = "builtin|http|mcp", message = "不支持的工具来源")
    private String sourceType;

    /** 按启停筛选。 */
    private Boolean enabled;

    /** 按名称或工具标识模糊匹配。 */
    @Size(max = 128, message = "关键字最长 128 字符")
    private String keyword;

    public String getCursor() {
        return cursor;
    }

    public void setCursor(String cursor) {
        this.cursor = cursor;
    }

    public Integer getSize() {
        return size;
    }

    public void setSize(Integer size) {
        this.size = size;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }
}
