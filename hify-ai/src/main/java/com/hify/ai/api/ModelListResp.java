package com.hify.ai.api;

import java.util.List;

/** 一页模型。游标分页，不返回总数。 */
public class ModelListResp {

    /** 本页模型。 */
    private List<ModelSummaryResp> items;

    /** 是否还有下一页。 */
    private Boolean hasNext;

    /** 下一页游标；没有下一页时为 null。 */
    private String nextCursor;

    public List<ModelSummaryResp> getItems() {
        return items;
    }

    public void setItems(List<ModelSummaryResp> items) {
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
