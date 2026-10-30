package com.hify.shared.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 插件配置。
 *
 * <p><b>分页拦截器必须注册</b>：不注册的话 {@code selectPage} 不会生成 LIMIT，
 * 会把整表查出来——「分页参数看起来生效了、实际没分页」是最难发现的一类问题。
 *
 * <p>{@code maxLimit} 是全局硬上限，即使代码里写了更大的 size 也不会真的查那么多
 * （CLAUDE.md §3.3：单页上限 100，管理台 200）。
 */
@Configuration(proxyBeanMethods = false)
public class MybatisPlusConfig {

    /** 全局单页条数硬上限，与 {@code PageQuery.MAX_SIZE_ADMIN} 对齐。 */
    private static final long MAX_LIMIT = 200L;

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        // 兜底上限：代码里写了更大的 size 也拦得住
        pagination.setMaxLimit(MAX_LIMIT);
        // 页码超出总页数时返回空列表，而不是绕回第一页
        pagination.setOverflow(false);
        interceptor.addInnerInterceptor(pagination);

        return interceptor;
    }
}
