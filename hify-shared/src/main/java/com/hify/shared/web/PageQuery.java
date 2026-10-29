package com.hify.shared.web;

/**
 * 分页查询参数。
 *
 * <p>统一收敛分页参数与**单页条数上限**——上限如果散落在各 controller，
 * 迟早有一处被漏掉，变成一次无意的全表扫描。
 *
 * <p>可以直接作为 Spring MVC 的绑定参数：
 * <pre>
 * &#64;GetMapping("/list")
 * public Result&lt;PageResult&lt;AgentDTO&gt;&gt; list(PageQuery query) { ... }
 * </pre>
 *
 * <p>注意 {@code maxSize} 是 final 且没有 setter——Spring 不会把它当成请求参数绑定，
 * 否则客户端可以用 {@code ?maxSize=1000000} 绕过上限。
 */
public class PageQuery {

    /** 默认每页条数。 */
    public static final int DEFAULT_SIZE = 20;

    /** 普通接口的单页上限。 */
    public static final int MAX_SIZE = 100;

    /** 管理台的单页上限。 */
    public static final int MAX_SIZE_ADMIN = 200;

    private int page = 1;

    private int size = DEFAULT_SIZE;

    private final int maxSize;

    /** 使用默认上限 {@link #MAX_SIZE}。 */
    public PageQuery() {
        this(MAX_SIZE);
    }

    /**
     * 指定单页上限。
     *
     * @param maxSize 单页上限，小于 1 时按 {@link #MAX_SIZE} 处理
     */
    public PageQuery(int maxSize) {
        this.maxSize = maxSize < 1 ? MAX_SIZE : maxSize;
    }

    /**
     * 构造普通接口的分页参数。
     *
     * @param page 页码，从 1 开始
     * @param size 每页条数，超过上限会被截断
     */
    public static PageQuery of(int page, int size) {
        PageQuery query = new PageQuery(MAX_SIZE);
        query.setPage(page);
        query.setSize(size);
        return query;
    }

    /**
     * 构造管理台的分页参数，上限为 {@link #MAX_SIZE_ADMIN}。
     *
     * @param page 页码，从 1 开始
     * @param size 每页条数，超过上限会被截断
     */
    public static PageQuery admin(int page, int size) {
        PageQuery query = new PageQuery(MAX_SIZE_ADMIN);
        query.setPage(page);
        query.setSize(size);
        return query;
    }

    public int getPage() {
        return page;
    }

    /** 页码，小于 1 时归一为 1。 */
    public void setPage(int page) {
        this.page = Math.max(1, page);
    }

    public int getSize() {
        return size;
    }

    /** 每页条数，小于 1 时归一为 1，超过上限时截断到上限。 */
    public void setSize(int size) {
        this.size = Math.min(Math.max(1, size), maxSize);
    }

    /** 当前生效的单页上限。 */
    public int getMaxSize() {
        return maxSize;
    }

    /** SQL 的 OFFSET 值。 */
    public int getOffset() {
        return (page - 1) * size;
    }

    /** SQL 的 LIMIT 值，等于 {@link #getSize()}。 */
    public int getLimit() {
        return size;
    }

    @Override
    public String toString() {
        return "PageQuery{page=" + page + ", size=" + size + ", maxSize=" + maxSize + '}';
    }
}
