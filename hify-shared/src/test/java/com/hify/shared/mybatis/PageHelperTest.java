package com.hify.shared.mybatis;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hify.shared.web.PageQuery;
import com.hify.shared.web.PageResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** {@link PageHelper} 的单元测试。 */
class PageHelperTest {

    @Test
    @DisplayName("toPage：页码从 1 开始，原样带过去")
    void toPageKeepsOneBasedPage() {
        Page<String> page = PageHelper.toPage(3, 20);
        assertThat(page.getCurrent()).isEqualTo(3L);
        assertThat(page.getSize()).isEqualTo(20L);
    }

    @Test
    @DisplayName("toPage：页码小于 1 归一为 1")
    void toPageNormalizesPage() {
        assertThat(PageHelper.toPage(0, 20).getCurrent()).isEqualTo(1L);
        assertThat(PageHelper.toPage(-5, 20).getCurrent()).isEqualTo(1L);
    }

    @Test
    @DisplayName("toPage：每页条数默认 20")
    void toPageUsesDefaultSize() {
        assertThat(PageHelper.toPage(1, 0).getSize()).isEqualTo(20L);
        assertThat(PageHelper.toPage(1, -1).getSize()).isEqualTo(20L);
    }

    @Test
    @DisplayName("toPage：每页条数最大 100，超出截断")
    void toPageCapsSize() {
        assertThat(PageHelper.toPage(1, 100).getSize()).isEqualTo(100L);
        assertThat(PageHelper.toPage(1, 101).getSize()).isEqualTo(100L);
        assertThat(PageHelper.toPage(1, 100_000).getSize()).isEqualTo(100L);
    }

    @Test
    @DisplayName("toPage(PageQuery)：沿用 PageQuery 自己的上限，管理台能用 200")
    void toPageFromQueryKeepsAdminLimit() {
        Page<String> admin = PageHelper.toPage(PageQuery.admin(1, 200));
        assertThat(admin.getSize()).isEqualTo(200L);

        Page<String> normal = PageHelper.toPage(PageQuery.of(1, 200));
        assertThat(normal.getSize()).isEqualTo(100L);
    }

    @Test
    @DisplayName("toPageResult：记录、总数、页码、条数原样带过去")
    void toPageResultCarriesMetadata() {
        Page<String> page = new Page<>(2, 20);
        page.setRecords(List.of("a", "b"));
        page.setTotal(57L);

        PageResult<String> result = PageHelper.toPageResult(page);

        assertThat(result.getCode()).isZero();
        assertThat(result.getData()).containsExactly("a", "b");
        assertThat(result.getTotal()).isEqualTo(57L);
        assertThat(result.getPage()).isEqualTo(2);
        assertThat(result.getSize()).isEqualTo(20);
    }

    @Test
    @DisplayName("toPageResult：带转换函数时逐条转成 DTO，元数据不变")
    void toPageResultAppliesConverter() {
        IPage<Row> page = new Page<>(1, 10);
        page.setRecords(List.of(new Row(1L, "第一条"), new Row(2L, "第二条")));
        page.setTotal(2L);

        PageResult<String> result = PageHelper.toPageResult(page, Row::text);

        assertThat(result.getData()).containsExactly("第一条", "第二条");
        assertThat(result.getTotal()).isEqualTo(2L);
        assertThat(result.getPage()).isEqualTo(1);
        assertThat(result.getSize()).isEqualTo(10);
    }

    /** 探针记录，模拟 DO。 */
    record Row(Long id, String text) {
    }
}
