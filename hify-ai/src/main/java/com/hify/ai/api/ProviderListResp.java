package com.hify.ai.api;

import java.util.List;

/** 接入点游标列表，无总数与页码。 */
public class ProviderListResp {

    /** 本页接入点。 */
    private List<ProviderSummaryResp> items;

    /** 是否还有下一页。 */
    private Boolean hasNext;

    /** 下页游标，无下页时为空。 */
    private String nextCursor;

    public List<ProviderSummaryResp> getItems() {
        return items;
    }

    public void setItems(List<ProviderSummaryResp> items) {
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
