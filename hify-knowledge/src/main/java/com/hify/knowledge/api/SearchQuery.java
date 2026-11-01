package com.hify.knowledge.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 知识库检索的查询条件。 */
public class SearchQuery {

    /** 检索文本。 */
    @NotBlank(message = "检索内容不能为空")
    @Size(max = 2000, message = "检索内容最长 2000 字符")
    private String query;

    /** 返回条数。 */
    @Min(value = 1, message = "返回条数不能小于 1")
    @Max(value = 20, message = "返回条数不能大于 20")
    private Integer topK;

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public Integer getTopK() {
        return topK;
    }

    public void setTopK(Integer topK) {
        this.topK = topK;
    }
}
