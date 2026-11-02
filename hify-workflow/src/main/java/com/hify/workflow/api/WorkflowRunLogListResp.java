package com.hify.workflow.api;

import java.util.List;

/** 一页运行记录。游标分页，不返回总数。 */
public class WorkflowRunLogListResp {

    /** 本页运行记录。 */
    private List<WorkflowRunLogResp> items;

    /** 是否还有下一页。 */
    private Boolean hasNext;

    /** 下一页游标；没有下一页时为 null。 */
    private String nextCursor;

    public List<WorkflowRunLogResp> getItems() {
        return items;
    }

    public void setItems(List<WorkflowRunLogResp> items) {
        this.items = items;
    }

    public Boolean getHasNext() {
        return hasNext;
    }

    public void setHasNext(Boolean hasNext) {
        this.hasNext = hasNext;
    }

    public String getNextCursor() {
        return nextCursor;
    }

    public void setNextCursor(String nextCursor) {
        this.nextCursor = nextCursor;
    }
}
