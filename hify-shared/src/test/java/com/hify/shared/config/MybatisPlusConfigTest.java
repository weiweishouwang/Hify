package com.hify.shared.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** {@link MybatisPlusConfig} 的单元测试。 */
class MybatisPlusConfigTest {

    @Test
    @DisplayName("注册了分页拦截器，否则 selectPage 不会生成 LIMIT")
    void paginationInterceptorIsRegistered() {
        MybatisPlusInterceptor interceptor = new MybatisPlusConfig().mybatisPlusInterceptor();

        assertThat(interceptor.getInterceptors())
                .anyMatch(PaginationInnerInterceptor.class::isInstance);
    }

    @Test
    @DisplayName("分页拦截器带全局上限 200，超出总页数返回空列表")
    void paginationInterceptorHasSafeLimits() {
        MybatisPlusInterceptor interceptor = new MybatisPlusConfig().mybatisPlusInterceptor();

        PaginationInnerInterceptor pagination = interceptor.getInterceptors().stream()
                .filter(PaginationInnerInterceptor.class::isInstance)
                .map(PaginationInnerInterceptor.class::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError("没有注册分页拦截器"));

        assertThat(pagination.getMaxLimit()).isEqualTo(200L);
        assertThat(pagination.isOverflow()).isFalse();
    }
}
