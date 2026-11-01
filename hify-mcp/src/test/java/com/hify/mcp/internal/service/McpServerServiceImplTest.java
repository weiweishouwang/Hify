package com.hify.mcp.internal.service;

import com.hify.mcp.api.McpServerCreateReq;
import com.hify.mcp.api.McpServerListQuery;
import com.hify.mcp.api.McpServerUpdateReq;
import com.hify.mcp.internal.client.McpClientManager;
import com.hify.mcp.internal.converter.McpConverter;
import com.hify.mcp.internal.entity.McpServerDO;
import com.hify.mcp.internal.mapper.McpServerMapper;
import com.hify.shared.crypto.CredentialCipher;
import com.hify.shared.crypto.SensitiveHeaderCipher;
import com.hify.shared.exception.BizException;
import com.hify.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpServerServiceImplTest {

    @Mock McpServerMapper servers;
    @Mock McpClientManager clients;
    @Mock McpToolDiscovery discovery;

    SensitiveHeaderCipher headerCipher;
    McpServerServiceImpl service;

    @BeforeEach
    void setUp() {
        headerCipher = new SensitiveHeaderCipher(new CredentialCipher(
                Base64.getEncoder().encodeToString(new byte[32]), "hify-credential-v1"));
        service = new McpServerServiceImpl(servers, clients, discovery, headerCipher,
                new McpConverter(headerCipher));
        lenient().when(servers.findIdByName(anyString())).thenReturn(null);
    }

    static McpServerCreateReq httpRequest() {
        var req = new McpServerCreateReq();
        req.setName("文件服务");
        req.setTransport("http");
        req.setUrl("http://127.0.0.1:9000/mcp");
        req.setHeaders(Map.of("Authorization", "Bearer secret", "X-Tenant", "acme"));
        return req;
    }

    static McpServerDO entity() {
        var entity = new McpServerDO();
        entity.setId(1L);
        entity.setPublicId("01MCPSERVER00000000000000001");
        entity.setName("文件服务");
        entity.setTransport("http");
        entity.setUrl("http://127.0.0.1:9000/mcp");
        entity.setEnabled(true);
        entity.setStatus("unknown");
        entity.setConfigVersion(1L);
        entity.setCreatedAt(LocalDateTime.of(2026, 11, 1, 10, 0));
        entity.setUpdatedAt(LocalDateTime.of(2026, 11, 1, 10, 0));
        return entity;
    }

    @Test
    @DisplayName("创建 http 服务：敏感请求头加密落库，明文不留在 config 里")
    void createEncryptsSensitiveHeaders() {
        var resp = service.create(httpRequest());

        assertThat(resp.getName()).isEqualTo("文件服务");
        assertThat(resp.getTransport()).isEqualTo("http");
        assertThat(resp.getHeaders().get("Authorization")).isEqualTo("******");
        assertThat(resp.getHeaders().get("X-Tenant")).isEqualTo("acme");

        var captor = ArgumentCaptor.forClass(McpServerDO.class);
        verify(servers).insert(captor.capture());
        assertThat(captor.getValue().getHeaders().toString()).doesNotContain("secret");
    }

    @Test
    @DisplayName("stdio 传输必须配置启动命令")
    void stdioRequiresCommand() {
        var req = httpRequest();
        req.setTransport("stdio");
        req.setCommand(null);

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("启动命令");
    }

    @Test
    @DisplayName("http 传输必须配置服务地址，且只允许 http/https")
    void httpRequiresUrl() {
        var req = httpRequest();
        req.setUrl(null);
        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("服务地址");

        var bad = httpRequest();
        bad.setUrl("ftp://example.com/mcp");
        assertThatThrownBy(() -> service.create(bad))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("http");
    }

    @Test
    @DisplayName("创建时名称已被占用（含已删除记录）报冲突")
    void createRejectsDuplicateName() {
        when(servers.findIdByName("文件服务")).thenReturn(99L);

        assertThatThrownBy(() -> service.create(httpRequest()))
                .isInstanceOf(BizException.class)
                .extracting(exception -> ((BizException) exception).getErrorCode())
                .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    @DisplayName("更新时配置版本不匹配报冲突")
    void updateRejectsStaleVersion() {
        when(servers.selectOne(any())).thenReturn(entity());

        var req = new McpServerUpdateReq();
        req.setName("新名字");
        req.setConfigVersion(99L);

        assertThatThrownBy(() -> service.update("01MCPSERVER00000000000000001", req))
                .isInstanceOf(BizException.class)
                .extracting(exception -> ((BizException) exception).getErrorCode())
                .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    @DisplayName("更新成功后断开旧连接 —— 旧连接连的是老地址")
    void updateEvictsClient() {
        when(servers.selectOne(any())).thenReturn(entity());
        when(servers.update(any(), any())).thenReturn(1);

        var req = new McpServerUpdateReq();
        req.setUrl("http://127.0.0.1:9001/mcp");
        req.setConfigVersion(1L);

        var resp = service.update("01MCPSERVER00000000000000001", req);

        assertThat(resp.getConfigVersion()).isEqualTo(2L);
        assertThat(resp.getUrl()).isEqualTo("http://127.0.0.1:9001/mcp");
        verify(clients).evict("01MCPSERVER00000000000000001");
    }

    @Test
    @DisplayName("删除服务时断开它的连接")
    void deleteEvictsClient() {
        when(servers.selectOne(any())).thenReturn(entity());
        when(servers.deleteById(any(java.io.Serializable.class))).thenReturn(1);

        service.delete("01MCPSERVER00000000000000001");

        verify(clients).evict("01MCPSERVER00000000000000001");
    }

    @Test
    @DisplayName("列表用游标分页，多取一条判断还有没有下一页")
    void listUsesCursor() {
        var first = entity();
        var second = entity();
        second.setId(2L);
        second.setPublicId("01MCPSERVER00000000000000002");
        second.setCreatedAt(LocalDateTime.of(2026, 11, 1, 9, 0));
        when(servers.selectList(any())).thenReturn(List.of(first, second));

        var query = new McpServerListQuery();
        query.setSize(1);
        var resp = service.list(query);

        assertThat(resp.getItems()).hasSize(1);
        assertThat(resp.getHasNext()).isTrue();
        assertThat(resp.getNextCursor()).isNotBlank();
    }

    @Test
    @DisplayName("连接测试失败时返回失败原因而不是抛异常")
    void testReportsFailure() {
        var stored = entity();
        when(servers.selectOne(any())).thenReturn(stored);
        when(discovery.discover(stored)).thenReturn(new McpToolDiscovery.Discovery(
                stored.getPublicId(), stored.getName(), false, List.of(), "连接失败：连接被拒绝", null));

        var resp = service.test("01MCPSERVER00000000000000001");

        assertThat(resp.connected()).isFalse();
        assertThat(resp.message()).contains("连接被拒绝");
    }
}
