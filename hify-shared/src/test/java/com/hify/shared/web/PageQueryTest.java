package com.hify.shared.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** {@link PageQuery} 的单元测试。 */
class PageQueryTest {

    @Test
    @DisplayName("默认第 1 页、每页 20 条")
    void 默认值() {
        PageQuery query = new PageQuery();

        assertThat(query.getPage()).isEqualTo(1);
        assertThat(query.getSize()).isEqualTo(PageQuery.DEFAULT_SIZE);
        assertThat(query.getMaxSize()).isEqualTo(PageQuery.MAX_SIZE);
        assertThat(query.getOffset()).isZero();
        assertThat(query.getLimit()).isEqualTo(PageQuery.DEFAULT_SIZE);
    }

    @Test
    @DisplayName("页码小于 1 时归一为 1")
    void 页码归一() {
        PageQuery query = new PageQuery();
        query.setPage(0);
        assertThat(query.getPage()).isEqualTo(1);

        query.setPage(-5);
        assertThat(query.getPage()).isEqualTo(1);
    }

    @Test
    @DisplayName("每页条数小于 1 时归一为 1")
    void 条数归一() {
        PageQuery query = new PageQuery();
        query.setSize(0);
        assertThat(query.getSize()).isEqualTo(1);

        query.setSize(-10);
        assertThat(query.getSize()).isEqualTo(1);
    }

    @Test
    @DisplayName("超过单页上限时被截断")
    void 超过上限截断() {
        PageQuery query = new PageQuery();
        query.setSize(999_999);

        assertThat(query.getSize()).isEqualTo(PageQuery.MAX_SIZE);
    }

    @Test
    @DisplayName("管理台上限放宽到 200")
    void 管理台上限() {
        PageQuery query = PageQuery.admin(1, 999_999);

        assertThat(query.getMaxSize()).isEqualTo(PageQuery.MAX_SIZE_ADMIN);
        assertThat(query.getSize()).isEqualTo(PageQuery.MAX_SIZE_ADMIN);
    }

    @Test
    @DisplayName("普通接口工厂的默认值与上限")
    void 普通接口工厂() {
        PageQuery query = PageQuery.of(3, 50);

        assertThat(query.getPage()).isEqualTo(3);
        assertThat(query.getSize()).isEqualTo(50);
        assertThat(query.getMaxSize()).isEqualTo(PageQuery.MAX_SIZE);
    }

    @Test
    @DisplayName("OFFSET 按页码与每页条数计算")
    void offset计算() {
        PageQuery query = PageQuery.of(3, 25);

        assertThat(query.getOffset()).isEqualTo(50);
        assertThat(query.getLimit()).isEqualTo(25);
    }

    @Test
    @DisplayName("非法上限按默认上限处理")
    void 非法上限兜底() {
        assertThat(new PageQuery(0).getMaxSize()).isEqualTo(PageQuery.MAX_SIZE);
        assertThat(new PageQuery(-1).getMaxSize()).isEqualTo(PageQuery.MAX_SIZE);
    }

    @Test
    @DisplayName("上限字段没有 setter，Spring 不会把它当请求参数绑定")
    void 上限不可绑定() {
        boolean hasSetter = false;
        for (java.lang.reflect.Method method : PageQuery.class.getMethods()) {
            if (method.getName().equals("setMaxSize")) {
                hasSetter = true;
            }
        }

        assertThat(hasSetter).isFalse();
    }
}
