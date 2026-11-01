package com.hify.ai.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 模型列表的查询条件：游标分页 + 接入点 / 用途 / 启停 / 关键字筛选。 */
public class ModelListQuery {

    /** 游标，取上一页返回的 nextCursor。 */
    @Size(max = 512, message = "游标过长")
    private String cursor;

    /** 每页条数。 */
    @Min(value = 1, message = "每页条数不能小于 1")
    @Max(value = 200, message = "每页条数不能大于 200")
    private Integer size;

    /** 按接入点过滤，接入点的对外标识。 */
    @Size(max = 26, message = "接入点标识过长")
    private String providerPublicId;

    /** 按用途过滤：chat / embedding。 */
    @Pattern(regexp = "chat|embedding", message = "不支持的模型用途")
    private String modelType;

    /** 按启停筛选。 */
    private Boolean enabled;

    /** 按展示名或上游模型标识模糊匹配。 */
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

    public String getProviderPublicId() {
        return providerPublicId;
    }

    public void setProviderPublicId(String providerPublicId) {
        this.providerPublicId = providerPublicId;
    }

    public String getModelType() {
        return modelType;
    }

    public void setModelType(String modelType) {
        this.modelType = modelType;
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
