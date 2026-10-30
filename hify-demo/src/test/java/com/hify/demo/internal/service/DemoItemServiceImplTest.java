package com.hify.demo.internal.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hify.demo.api.DemoItemCreateReq;
import com.hify.demo.api.DemoItemResp;
import com.hify.demo.api.DemoItemUpdateReq;
import com.hify.demo.internal.entity.DemoItemDO;
import com.hify.demo.internal.mapper.DemoItemMapper;
import com.hify.shared.exception.BizException;
import com.hify.shared.web.PageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** {@link DemoItemServiceImpl} 的单元测试。 */
@ExtendWith(MockitoExtension.class)
class DemoItemServiceImplTest {

    @Mock
    private DemoItemMapper mapper;

    private DemoItemServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DemoItemServiceImpl(mapper);
    }

    private static DemoItemDO entity(long id, String name, int status) {
        DemoItemDO entity = new DemoItemDO();
        entity.setId(id);
        entity.setName(name);
        entity.setStatus(status);
        return entity;
    }

    @Test
    @DisplayName("list：把分页结果转成 PageResult，并带上总数与页码")
    @SuppressWarnings("unchecked")
    void listConvertsToPageResult() {
        Page<DemoItemDO> page = new Page<>(2, 20);
        page.setRecords(List.of(entity(3L, "第三条", 1), entity(2L, "第二条", 0)));
        page.setTotal(37L);
        when(mapper.selectPage(any(IPage.class), any(Wrapper.class))).thenReturn(page);

        PageResult<DemoItemResp> result = service.list(2, 20);

        assertThat(result.getCode()).isZero();
        assertThat(result.getTotal()).isEqualTo(37L);
        assertThat(result.getPage()).isEqualTo(2);
        assertThat(result.getSize()).isEqualTo(20);
        assertThat(result.getData()).extracting(DemoItemResp::getName)
                .containsExactly("第三条", "第二条");
    }

    @Test
    @DisplayName("list：每页条数超过上限会被截断到 100")
    @SuppressWarnings("unchecked")
    void listCapsPageSize() {
        when(mapper.selectPage(any(IPage.class), any(Wrapper.class)))
                .thenReturn(new Page<>(1, 100));

        service.list(1, 9999);

        ArgumentCaptor<IPage<DemoItemDO>> captor = ArgumentCaptor.forClass(IPage.class);
        verify(mapper).selectPage(captor.capture(), any(Wrapper.class));
        assertThat(captor.getValue().getSize()).isEqualTo(100L);
    }

    @Test
    @DisplayName("get：查不到时抛 NOT_FOUND，不返回 null")
    void getThrowsWhenMissing() {
        when(mapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> service.get(99L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("演示条目不存在");
    }

    @Test
    @DisplayName("create：落库并把回填的主键带进响应")
    void createInsertsAndReturns() {
        when(mapper.insert(any(DemoItemDO.class))).thenAnswer(invocation -> {
            DemoItemDO entity = invocation.getArgument(0);
            entity.setId(7L);
            return 1;
        });
        DemoItemCreateReq req = new DemoItemCreateReq();
        req.setName("新建的");
        req.setStatus(1);

        DemoItemResp resp = service.create(req);

        assertThat(resp.getId()).isEqualTo(7L);
        assertThat(resp.getName()).isEqualTo("新建的");
        assertThat(resp.getStatus()).isEqualTo(1);
        verify(mapper).insert(any(DemoItemDO.class));
    }

    @Test
    @DisplayName("update：存在时更新字段")
    void updateModifiesExisting() {
        when(mapper.selectById(1L)).thenReturn(entity(1L, "旧名字", 0));
        when(mapper.updateById(any(DemoItemDO.class))).thenReturn(1);
        DemoItemUpdateReq req = new DemoItemUpdateReq();
        req.setName("新名字");
        req.setStatus(2);

        DemoItemResp resp = service.update(1L, req);

        assertThat(resp.getName()).isEqualTo("新名字");
        assertThat(resp.getStatus()).isEqualTo(2);
        verify(mapper).updateById(any(DemoItemDO.class));
    }

    @Test
    @DisplayName("update：查不到时抛 NOT_FOUND")
    void updateThrowsWhenMissing() {
        when(mapper.selectById(99L)).thenReturn(null);
        DemoItemUpdateReq req = new DemoItemUpdateReq();
        req.setName("新名字");
        req.setStatus(2);

        assertThatThrownBy(() -> service.update(99L, req)).isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("delete：逻辑删除，由 @TableLogic 改写成 UPDATE")
    void deleteRemovesLogically() {
        when(mapper.deleteById(1L)).thenReturn(1);

        service.delete(1L);

        verify(mapper).deleteById(1L);
    }

    @Test
    @DisplayName("delete：删不掉时抛 NOT_FOUND")
    void deleteThrowsWhenMissing() {
        when(mapper.deleteById(99L)).thenReturn(0);

        assertThatThrownBy(() -> service.delete(99L)).isInstanceOf(BizException.class);
    }
}
