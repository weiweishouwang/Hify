package com.hify.workflow.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 工作流列表的查询条件：游标分页 + 状态 / 关键字筛选。 */
public class WorkflowListQuery {

    /** 游标，取上一页返回的 nextCursor。 */
    @Size(max = 512, message = "游标过长")
    private String cursor;

    /** 每页条数。 */
    @Min(value = 1, message = "每页条数不能小于 1")
    @Max(value = 200, message = "每页条数不能大于 200")
    private Integer size;

    /** 按状态过滤：draft / published / disabled。 */
    @Pattern(regexp = "draft|published|disabled", message = "不支持的工作流状态")
    private String status;

    /** 按名称或描述模糊匹配。 */
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }
}
