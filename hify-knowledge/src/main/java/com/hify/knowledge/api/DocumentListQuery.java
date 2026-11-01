package com.hify.knowledge.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** 文档列表的查询条件：游标分页 + 状态筛选。 */
public class DocumentListQuery {

    /** 游标，取上一页返回的 nextCursor。 */
    @Size(max = 512, message = "游标过长")
    private String cursor;

    /** 每页条数。 */
    @Min(value = 1, message = "每页条数不能小于 1")
    @Max(value = 200, message = "每页条数不能大于 200")
    private Integer size;

    /** 按状态筛选：uploaded / indexing / ready / failed / deleting。 */
    @Size(max = 32, message = "状态过长")
    private String status;

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
}
