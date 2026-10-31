package com.hify.ai.api;

import com.hify.shared.web.PageQuery;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** 模型接入点的筛选与游标参数，不提供页码跳转。 */
public class ProviderListQuery {

    /** 协议筛选，不传表示全部。 */
    @Pattern(regexp = ProviderCreateReq.PROTOCOL_PATTERN, message = "type 必须为 openai、anthropic 或 ollama")
    private String type;

    /** 启停筛选，不传表示全部。 */
    private Boolean enabled;

    /** 单页条数，默认20，服务端限制最多200。 */
    @Positive(message = "size 必须大于0")
    @Max(value = PageQuery.MAX_SIZE_ADMIN, message = "size 不能超过200")
    private Integer size;

    /** 上一页返回的不透明游标。 */
    @Size(max = 512)
    private String cursor;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Integer getSize() {
        return size;
    }

    public void setSize(Integer size) {
        this.size = size;
    }

    public String getCursor() {
        return cursor;
    }

    public void setCursor(String cursor) {
        this.cursor = cursor;
    }

}
