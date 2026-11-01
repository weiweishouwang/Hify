package com.hify.agent.api;

import java.util.List;

/** 一页 Agent。游标分页，不返回总数。 */
public class AgentListResp {

    /** 本页 Agent。 */
    private List<AgentSummaryResp> items;

    /** 是否还有下一页。 */
    private Boolean hasNext;

    /** 下一页游标；没有下一页时为 null。 */
    private String nextCursor;

    public List<AgentSummaryResp> getItems() {
        return items;
    }

    public void setItems(List<AgentSummaryResp> items) {
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
