package com.hify.shared.web;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 分页响应封装。
 *
 * <p>继承 {@link Result}，{@code data} 承载当前页的记录列表，额外提供 {@code total}（总条数）、
 * {@code page}（当前页码，从 1 开始）、{@code size}（每页条数）。
 *
 * <p>构造器带 {@link JsonCreator} 是为了 Jackson 能反序列化，每个参数都显式标注 {@link JsonProperty}，
 * 不依赖 {@code ParameterNamesModule}。
 *
 * @param <T> 单条记录的类型
 */
public class PageResult<T> extends Result<List<T>> {

    private static final long serialVersionUID = 1L;

    private final long total;

    private final int page;

    private final int size;

    @JsonCreator
    public PageResult(@JsonProperty("code") int code,
                      @JsonProperty("message") String message,
                      @JsonProperty("data") List<T> data,
                      @JsonProperty("total") long total,
                      @JsonProperty("page") int page,
                      @JsonProperty("size") int size) {
        super(code, message, data);
        this.total = total;
        this.page = page;
        this.size = size;
    }

    /**
     * 分页成功。
     *
     * @param records 当前页记录
     * @param total   总条数
     * @param page    当前页码，从 1 开始
     * @param size    每页条数
     */
    public static <T> PageResult<T> of(List<T> records, long total, int page, int size) {
        return new PageResult<>(SUCCESS_CODE, SUCCESS_MESSAGE, records, total, page, size);
    }

    public long getTotal() {
        return total;
    }

    public int getPage() {
        return page;
    }

    public int getSize() {
        return size;
    }

    @Override
    public String toString() {
        return "PageResult{code=" + getCode()
                + ", message='" + getMessage() + '\''
                + ", total=" + total
                + ", page=" + page
                + ", size=" + size
                + ", records=" + (getData() == null ? 0 : getData().size())
                + '}';
    }
}
