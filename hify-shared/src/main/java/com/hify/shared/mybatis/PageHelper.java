package com.hify.shared.mybatis;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hify.shared.web.PageQuery;
import com.hify.shared.web.PageResult;

import java.util.List;
import java.util.function.Function;

/**
 * 分页参数与结果的转换工具。
 *
 * <p>把「前端传来的页码/每页条数」和「MyBatis-Plus 的 {@link IPage}」这两头都收口到一处：
 * 上限、默认值、页码基数只在这里定一次，各 controller 不会再各写一套。
 *
 * <p><b>注意类名</b>：与另一套 MyBatis 分页库 {@code com.github.pagehelper.PageHelper} 同名。
 * 本项目没有引入那套库，不冲突；但如果将来引入了，import 时要写全限定名。
 *
 * <p><b>前提</b>：分页拦截器必须注册（见 {@code shared.config.MybatisPlusConfig}），
 * 否则 {@code selectPage} 不会生成 LIMIT，{@link #toPage} 产出的对象形同虚设。
 */
public final class PageHelper {

    /** 默认每页条数。 */
    public static final int DEFAULT_SIZE = PageQuery.DEFAULT_SIZE;

    /** 普通接口的单页上限。 */
    public static final int MAX_SIZE = PageQuery.MAX_SIZE;

    private PageHelper() {
    }

    /**
     * 把前端分页参数转成 MyBatis-Plus 的 {@link Page}。
     *
     * @param page 页码，从 1 开始；小于 1 时归一为 1
     * @param size 每页条数；小于 1 时取默认 {@value #DEFAULT_SIZE}，超过 {@value #MAX_SIZE} 时截断
     * @param <T>  记录类型
     * @return 可直接传给 {@code selectPage} 的分页对象
     */
    public static <T> Page<T> toPage(int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        return new Page<>(safePage, safeSize);
    }

    /**
     * 用已经归一化过的 {@link PageQuery} 转换。
     *
     * <p>管理台的单页上限是 {@link PageQuery#MAX_SIZE_ADMIN}（200），比普通接口宽，
     * 走这个重载才能用上——{@link #toPage(int, int)} 一律按 {@value #MAX_SIZE} 截断。
     *
     * @param query 分页参数
     * @param <T>   记录类型
     * @return 可直接传给 {@code selectPage} 的分页对象
     */
    public static <T> Page<T> toPage(PageQuery query) {
        return new Page<>(query.getPage(), query.getSize());
    }

    /**
     * 把查询结果转成对外的 {@link PageResult}。
     *
     * @param page MyBatis-Plus 的分页结果
     * @param <T>  记录类型
     * @return 分页响应
     */
    public static <T> PageResult<T> toPageResult(IPage<T> page) {
        return PageResult.of(page.getRecords(), page.getTotal(),
                (int) page.getCurrent(), (int) page.getSize());
    }

    /**
     * 把查询结果转成对外的 {@link PageResult}，同时把记录逐条转成 DTO。
     *
     * <p>这是实际最常用的形式：Mapper 查出的是 DO，对外要的是 DTO，
     * 而 total / page / size 三个元数据要原样带过去。
     *
     * @param page      MyBatis-Plus 的分页结果
     * @param converter DO → DTO 的转换函数
     * @param <S>       记录（DO）类型
     * @param <T>       对外记录（DTO）类型
     * @return 分页响应
     */
    public static <S, T> PageResult<T> toPageResult(IPage<S> page, Function<S, T> converter) {
        List<T> records = page.getRecords().stream().map(converter).toList();
        return PageResult.of(records, page.getTotal(),
                (int) page.getCurrent(), (int) page.getSize());
    }
}
