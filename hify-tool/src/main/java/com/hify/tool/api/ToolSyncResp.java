package com.hify.tool.api;

import java.util.List;

/** 目录同步的统计结果。 */
public class ToolSyncResp {

    /** 各来源的同步明细。 */
    private List<SourceStat> sources;

    /** 本次发现的工具总数。 */
    private Integer discovered;

    /** 新建的目录行数。 */
    private Integer created;

    /** 更新的目录行数。 */
    private Integer updated;

    /** 被下架的目录行数。 */
    private Integer disabled;

    public List<SourceStat> getSources() {
        return sources;
    }

    public void setSources(List<SourceStat> sources) {
        this.sources = sources;
    }

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

    /**
     * 单个来源的同步明细。
     *
     * @param sourceType 来源
     * @param discovered 该来源本次发现的工具数
     * @param created    新建的目录行数
     * @param updated    更新的目录行数
     * @param disabled   被下架的目录行数
     */
    public record SourceStat(String sourceType, int discovered, int created, int updated, int disabled) {
    }
}
