package com.hify.tool.internal.service;

import com.hify.shared.crypto.CredentialCipher;
import com.hify.shared.exception.BizException;
import com.hify.shared.exception.ErrorCode;
import com.hify.tool.api.ToolCreateReq;
import com.hify.tool.api.ToolDetailResp;
import com.hify.tool.api.ToolListQuery;
import com.hify.tool.api.ToolUpdateReq;
import com.hify.tool.internal.converter.ToolConverter;
import com.hify.tool.internal.entity.ToolDefinitionDO;
import com.hify.tool.internal.mapper.ToolDefinitionMapper;
import com.hify.shared.crypto.SensitiveHeaderCipher;
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
class ToolDefinitionServiceImplTest {

    @Mock ToolDefinitionMapper tools;
    @Mock ToolSyncService syncService;

    SensitiveHeaderCipher headerCipher;
    ToolDefinitionServiceImpl service;

    @BeforeEach
    void setUp() {
        headerCipher = new SensitiveHeaderCipher(new CredentialCipher(
                Base64.getEncoder().encodeToString(new byte[32]), "hify-credential-v1"));
        service = new ToolDefinitionServiceImpl(tools, new ToolConverter(headerCipher), headerCipher, syncService);
        lenient().when(tools.findIdByToolKey(anyString(), anyString())).thenReturn(null);
        lenient().when(tools.findIdByName(anyString())).thenReturn(null);
    }

    static ToolCreateReq createRequest() {
        var req = new ToolCreateReq();
        req.setName("天气查询");
        req.setSourceType("http");
        req.setToolKey("weather_query");
        req.setDescription("查天气");
        req.setUrl("https://api.example.com/weather");
        req.setMethod("POST");
        req.setHeaders(Map.of("Content-Type", "application/json", "Authorization", "Bearer secret"));
        return req;
    }

    static ToolDefinitionDO entity() {
        var entity = new ToolDefinitionDO();
        entity.setId(1L);
        entity.setPublicId("01JTESTTOOL00000000000001");
        entity.setSourceType("http");
        entity.setToolKey("weather_query");
        entity.setName("天气查询");
        entity.setDescription("查天气");
        entity.setEnabled(true);
        entity.setConfigVersion(1L);
        entity.setCreatedAt(LocalDateTime.of(2026, 10, 31, 12, 0));
        entity.setUpdatedAt(LocalDateTime.of(2026, 10, 31, 12, 0));
        entity.setConfig(Map.of(
                "url", "https://api.example.com/weather",
                "method", "POST",
                "timeoutMs", 30000,
                "headers", Map.of("Content-Type", "application/json")));
        return entity;
    }

    @Test
    @DisplayName("创建 HTTP 工具：敏感请求头加密落库，明文不留在 config 里")
    void createEncryptsHeaders() {
        when(tools.insert(any(ToolDefinitionDO.class))).thenReturn(1);

        ToolDetailResp resp = service.create(createRequest());

        assertThat(resp.getName()).isEqualTo("天气查询");
        assertThat(resp.getSourceType()).isEqualTo("http");
        assertThat(resp.getUrl()).isEqualTo("https://api.example.com/weather");
        assertThat(resp.getMethod()).isEqualTo("POST");

        var captor = ArgumentCaptor.forClass(ToolDefinitionDO.class);
        verify(tools).insert(captor.capture());
        ToolDefinitionDO saved = captor.getValue();
        assertThat(saved.getConfig().toString()).doesNotContain("secret");
        assertThat(saved.getConfigVersion()).isEqualTo(1L);
        assertThat(saved.getPublicId()).isNotBlank();
    }

    @Test
    @DisplayName("创建时工具标识已被占用（含已删除记录）报冲突")
    void createRejectsDuplicateToolKey() {
        when(tools.findIdByToolKey("http", "weather_query")).thenReturn(99L);

        assertThatThrownBy(() -> service.create(createRequest()))
                .isInstanceOf(BizException.class)
                .extracting(exception -> ((BizException) exception).getErrorCode())
                .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    @DisplayName("创建时工具名已被占用报冲突")
    void createRejectsDuplicateName() {
        when(tools.findIdByName("天气查询")).thenReturn(99L);

        assertThatThrownBy(() -> service.create(createRequest()))
                .isInstanceOf(BizException.class)
                .extracting(exception -> ((BizException) exception).getErrorCode())
                .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    @DisplayName("请求地址带查询串时拒绝，避免凭据出现在 URL 与访问日志里")
    void createRejectsUrlWithQuery() {
        var req = createRequest();
        req.setUrl("https://api.example.com/weather?token=abc");

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("查询参数");
    }

    @Test
    @DisplayName("详情里的敏感请求头被占位符替换")
    void detailMasksSecrets() {
        var stored = entity();
        stored.setConfig(Map.of(
                "url", "https://api.example.com/weather",
                "method", "POST",
                "timeoutMs", 30000,
                "headers", headerCipher.encryptHeaders(Map.of(
                        "Content-Type", "application/json", "Authorization", "Bearer secret"))));
        when(tools.selectOne(any())).thenReturn(stored);

        ToolDetailResp resp = service.get("01JTESTTOOL00000000000001");

        assertThat(resp.getHeaders().get("Authorization")).isEqualTo("******");
        assertThat(resp.getHeaders().get("Content-Type")).isEqualTo("application/json");
        assertThat(resp.getHeaders().toString()).doesNotContain("secret");
    }

    @Test
    @DisplayName("更新时配置版本不匹配报冲突")
    void updateRejectsStaleVersion() {
        when(tools.selectOne(any())).thenReturn(entity());

        var req = new ToolUpdateReq();
        req.setName("天气查询");
        req.setConfigVersion(99L);

        assertThatThrownBy(() -> service.update("01JTESTTOOL00000000000001", req))
                .isInstanceOf(BizException.class)
                .extracting(exception -> ((BizException) exception).getErrorCode())
                .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    @DisplayName("更新时把敏感请求头的占位符还原成原密文，其余按提交值替换")
    void updateKeepsMaskedSecret() {
        var stored = entity();
        var encrypted = headerCipher.encryptHeaders(Map.of(
                "Content-Type", "application/json", "Authorization", "Bearer secret"));
        stored.setConfig(Map.of(
                "url", "https://api.example.com/weather",
                "method", "POST",
                "timeoutMs", 30000,
                "headers", encrypted));
        when(tools.selectOne(any())).thenReturn(stored);
        when(tools.update(any(), any())).thenReturn(1);

        var req = new ToolUpdateReq();
        req.setName("天气查询");
        req.setHeaders(Map.of("Content-Type", "application/json", "Authorization", "******"));
        req.setConfigVersion(1L);

        service.update("01JTESTTOOL00000000000001", req);

        var captor = ArgumentCaptor.forClass(ToolDefinitionDO.class);
        verify(tools).update(captor.capture(), any());
        Map<?, ?> headers = (Map<?, ?>) captor.getValue().getConfig().get("headers");
        assertThat(headers.get("Authorization")).isEqualTo(encrypted.get("Authorization"));
        assertThat(captor.getValue().getConfigVersion()).isEqualTo(2L);
    }

    @Test
    @DisplayName("删除内置来源的工具被拒绝：它的行由同步机制托管")
    void deleteRejectsBuiltinSource() {
        var builtin = entity();
        builtin.setSourceType("builtin");
        when(tools.selectOne(any())).thenReturn(builtin);

        assertThatThrownBy(() -> service.delete("01JTESTTOOL00000000000001"))
                .isInstanceOf(BizException.class)
                .extracting(exception -> ((BizException) exception).getErrorCode())
                .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    @DisplayName("列表用游标分页，多取一条判断还有没有下一页")
    void listUsesCursor() {
        var first = entity();
        var second = entity();
        second.setId(2L);
        second.setPublicId("01JTESTTOOL00000000000002");
        second.setCreatedAt(LocalDateTime.of(2026, 10, 31, 11, 0));
        when(tools.selectList(any())).thenReturn(List.of(first, second));

        var query = new ToolListQuery();
        query.setSize(1);
        var resp = service.list(query);

        assertThat(resp.getItems()).hasSize(1);
        assertThat(resp.getHasNext()).isTrue();
        assertThat(resp.getNextCursor()).isNotBlank();
    }
}
