package com.hify.tool.internal.service;

import com.hify.tool.api.ToolProvider;
import com.hify.tool.api.ToolSpec;
import com.hify.tool.internal.entity.ToolDefinitionDO;
import com.hify.tool.internal.mapper.ToolDefinitionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 目录同步：新建、更新、下架与跳过。 */
@ExtendWith(MockitoExtension.class)
class ToolSyncServiceTest {

    @Mock ToolDefinitionMapper tools;
    @Mock ToolProvider provider;
    @Mock PlatformTransactionManager transactionManager;

    ToolSyncService service;

    @BeforeEach
    void setUp() {
        lenient().when(transactionManager.getTransaction(any())).thenReturn((TransactionStatus) new SimpleTransactionStatus());
        service = new ToolSyncService(List.of(provider), tools, transactionManager);
        lenient().when(provider.sourceType()).thenReturn("builtin");
        lenient().when(tools.findIdByToolKey(anyString(), anyString())).thenReturn(null);
        lenient().when(tools.findIdByName(anyString())).thenReturn(null);
        lenient().when(tools.listToolKeysBySource(anyString())).thenReturn(List.of());
    }

    static ToolSpec spec(String toolKey, String name) {
        return new ToolSpec(toolKey, name, "描述", Map.of("type", "object"), Map.of(), "builtin");
    }

    @Test
    @DisplayName("来源报告的工具在目录里不存在时新建一行")
    void createsMissingRow() {
        when(provider.listTools()).thenReturn(List.of(spec("current_time", "current_time")));

        var result = service.syncAll();

        assertThat(result.getDiscovered()).isEqualTo(1);
        assertThat(result.getCreated()).isEqualTo(1);
        verify(tools).insert(any(ToolDefinitionDO.class));
    }

    @Test
    @DisplayName("来源本次没提到的行被置为停用，不物理删除")
    void disablesVanished() {
        var vanished = new ToolDefinitionDO();
        vanished.setId(7L);
        vanished.setToolKey("old_tool");
        vanished.setSourceType("builtin");
        vanished.setEnabled(true);
        vanished.setConfigVersion(1L);

        when(provider.listTools()).thenReturn(List.of(spec("current_time", "current_time")));
        when(tools.listToolKeysBySource("builtin")).thenReturn(List.of("current_time", "old_tool"));
        when(tools.selectOne(any())).thenReturn(vanished);

        var result = service.syncAll();

        assertThat(result.getDisabled()).isEqualTo(1);
        verify(tools).updateById(vanished);
        assertThat(vanished.getEnabled()).isFalse();
        verify(tools, never()).deleteById(any(java.io.Serializable.class));
    }

    @Test
    @DisplayName("来源返回空列表时整体跳过：既不新建也不下架")
    void emptySourceIsSkipped() {
        when(provider.listTools()).thenReturn(List.of());

        var result = service.syncAll();

        assertThat(result.getDiscovered()).isZero();
        assertThat(result.getCreated()).isZero();
        assertThat(result.getDisabled()).isZero();
        verify(tools, never()).insert(any(ToolDefinitionDO.class));
        verify(tools, never()).updateById(any(ToolDefinitionDO.class));
    }

    @Test
    @DisplayName("工具名被别的工具占着时跳过同步，避免表里出现两个同名工具")
    void skipsOnNameConflict() {
        when(provider.listTools()).thenReturn(List.of(spec("current_time", "current_time")));
        when(tools.findIdByName("current_time")).thenReturn(42L);

        var result = service.syncAll();

        assertThat(result.getCreated()).isZero();
        verify(tools, never()).insert(any(ToolDefinitionDO.class));
    }

    @Test
    @DisplayName("目录行与来源一致时不改动，避免每次同步都顶高配置版本")
    void unchangedRowIsNotUpdated() {
        var existing = new ToolDefinitionDO();
        existing.setId(3L);
        existing.setSourceType("builtin");
        existing.setToolKey("current_time");
        existing.setName("current_time");
        existing.setDescription("描述");
        existing.setSchema(Map.of("type", "object"));
        existing.setConfig(Map.of());
        existing.setEnabled(true);
        existing.setConfigVersion(3L);

        when(provider.listTools()).thenReturn(List.of(spec("current_time", "current_time")));
        when(tools.findIdByToolKey("builtin", "current_time")).thenReturn(3L);
        when(tools.selectById(3L)).thenReturn(existing);

        var result = service.syncAll();

        assertThat(result.getUpdated()).isZero();
        verify(tools, never()).updateById(any(ToolDefinitionDO.class));
    }
}
