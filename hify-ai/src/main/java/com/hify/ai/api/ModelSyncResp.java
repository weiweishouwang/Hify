package com.hify.ai.api;

/** 模型目录同步的统计结果。 */
public class ModelSyncResp {

    /** 上游本次报告的模型数。 */
    private Integer discovered;

    /** 新建的模型行数。 */
    private Integer created;

    /** 更新的模型行数。 */
    private Integer updated;

    /** 上游不再提供、被停用的模型行数。 */
    private Integer disabled;

    public Integer getDiscovered() {
        return discovered;
    }

    public void setDiscovered(Integer discovered) {
        this.discovered = discovered;
    }

    public Integer getCreated() {
        return created;
    }

    public void setCreated(Integer created) {
        this.created = created;
    }

    public Integer getUpdated() {
        return updated;
    }

    public void setUpdated(Integer updated) {
        this.updated = updated;
    }

    public Integer getDisabled() {
        return disabled;
    }

    public void setDisabled(Integer disabled) {
        this.disabled = disabled;
    }
}
