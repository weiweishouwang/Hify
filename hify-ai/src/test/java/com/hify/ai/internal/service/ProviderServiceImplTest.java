package com.hify.ai.internal.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.hify.ai.api.ProviderCreateReq;
import com.hify.ai.api.ProviderUpdateReq;
import com.hify.ai.api.CredentialActionEnum;
import com.hify.ai.api.ProviderConfigResp;
import com.hify.ai.internal.entity.ModelProviderDO;
import com.hify.ai.internal.entity.ProviderHealthDO;
import com.hify.ai.internal.mapper.ModelMapper;
import com.hify.ai.internal.mapper.ModelProviderMapper;
import com.hify.ai.internal.mapper.ProviderHealthMapper;
import com.hify.ai.internal.security.ProviderCredentialCipher;
import com.hify.shared.exception.BizException;
import com.hify.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProviderServiceImplTest {
    @Mock ModelProviderMapper providers;
    @Mock ModelMapper models;
    @Mock ProviderHealthMapper health;
    @Mock ProviderConfigQueryService queries;
    @Mock ProviderConnectivityService connectivity;
    ProviderCredentialCipher cipher;
    ProviderServiceImpl service;

    @BeforeEach
    void setUp() {
        lenient().when(providers.findIdByName(anyString())).thenReturn(null);
        cipher = new ProviderCredentialCipher(Base64.getEncoder().encodeToString(new byte[32]));
        service = new ProviderServiceImpl(providers, models, health, queries, cipher, connectivity);
    }

    static ProviderCreateReq createRequest() {
        var req = new ProviderCreateReq();
        req.setName("OpenAI");
        req.setType("openai");
        req.setBaseUrl("https://api.openai.com/v1/");
        req.setAuthConfig(Map.of("apiKey", "test-secret"));
        return req;
    }

    static ModelProviderDO entity() {
        var entity = new ModelProviderDO();
        entity.setId(1L);
        entity.setPublicId("01JTESTPROVIDER000000000001");
        entity.setName("OpenAI");
        entity.setType("openai");
        entity.setBaseUrl("https://api.openai.com/v1");
        entity.setConfigVersion(1L);
        entity.setEnabled(true);
        entity.setAuthConfig(Map.of());
        return entity;
    }

    static ProviderUpdateReq updateRequest() {
        var req = new ProviderUpdateReq();
        req.setName("Updated");
        req.setType("openai");
        req.setBaseUrl("https://api.openai.com/v1");
        req.setConfigVersion(1L);
        return req;
    }

    @Test
    void createEncryptsCredentialsAndInitializesHealth() {
        when(providers.insert(any(ModelProviderDO.class))).thenAnswer(call -> {
            call.<ModelProviderDO>getArgument(0).setId(7L);
            return 1;
        });
        var response = service.create(createRequest());
        var provider = ArgumentCaptor.forClass(ModelProviderDO.class);
        verify(providers).insert(provider.capture());
        assertThat(provider.getValue().getPublicId()).hasSize(26);
        assertThat(provider.getValue().getConfigVersion()).isEqualTo(1L);
        assertThat(provider.getValue().getBaseUrl()).isEqualTo("https://api.openai.com/v1");
        assertThat(provider.getValue().getAuthConfig().get("apiKey")).isInstanceOf(Map.class);
        assertThat(provider.getValue().getAuthConfig().toString()).doesNotContain("test-secret");
        assertThat(response.getAuthConfig()).doesNotContainKey("apiKey");
        assertThat(response.getCredentialsConfigured()).isTrue();
        var observation = ArgumentCaptor.forClass(ProviderHealthDO.class);
        verify(health).insert(observation.capture());
        assertThat(observation.getValue().getProviderId()).isEqualTo(7L);
        assertThat(observation.getValue().getStatus()).isEqualTo("UNKNOWN");
        assertThat(observation.getValue().getFailCount()).isZero();
    }

    @Test
    void createRejectsReservedNameIncludingSoftDeletedRows() {
        when(providers.findIdByName("OpenAI")).thenReturn(99L);
        assertConflict(() -> service.create(createRequest()));
        verify(providers, never()).insert(any(ModelProviderDO.class));
    }

    @Test
    void concurrentDuplicateIsTranslatedAndKeepsCause() {
        var duplicate = new DuplicateKeyException("duplicate");
        when(providers.insert(any(ModelProviderDO.class))).thenThrow(duplicate);
        assertThatThrownBy(() -> service.create(createRequest()))
                .isInstanceOfSatisfying(BizException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.CONFLICT))
                .hasCause(duplicate);
    }

    @Test
    void updateKeepsEncryptedKeyAndIncrementsVersion() {
        var existing = entity();
        var encrypted = cipher.encrypt("keep-me");
        existing.setAuthConfig(Map.of("apiKey", encrypted));
        when(providers.selectOne(any(Wrapper.class))).thenReturn(existing);
        when(providers.update(any(ModelProviderDO.class), any(Wrapper.class))).thenReturn(1);
        var response = service.update(existing.getPublicId(), updateRequest());
        assertThat(response.getConfigVersion()).isEqualTo(2L);
        assertThat(response.getAuthConfig()).doesNotContainKey("apiKey");
        var captor = ArgumentCaptor.forClass(ModelProviderDO.class);
        verify(providers).update(captor.capture(), any(Wrapper.class));
        assertThat(captor.getValue().getAuthConfig().get("apiKey")).isEqualTo(encrypted);
    }

    @Test
    void updateReplacesAndClearsCredentialsExplicitly() {
        when(providers.selectOne(any(Wrapper.class))).thenReturn(entity());
        when(providers.update(any(ModelProviderDO.class), any(Wrapper.class))).thenReturn(1);
        var req = updateRequest();
        req.setCredentialAction(CredentialActionEnum.REPLACE);
        req.setAuthConfig(Map.of("apiKey", "replacement"));
        assertThat(service.update(entity().getPublicId(), req).getCredentialsConfigured()).isTrue();
        req.setCredentialAction(CredentialActionEnum.CLEAR);
        req.setAuthConfig(Map.of());
        assertThat(service.update(entity().getPublicId(), req).getCredentialsConfigured()).isFalse();
    }

    @Test
    void updateRejectsConflictingNameOrVersion() {
        when(providers.selectOne(any(Wrapper.class))).thenReturn(entity());
        var req = updateRequest();
        req.setConfigVersion(9L);
        assertConflict(() -> service.update(entity().getPublicId(), req));
        req.setConfigVersion(1L);
        when(providers.findIdByName("Updated")).thenReturn(2L);
        assertConflict(() -> service.update(entity().getPublicId(), req));
    }

    @Test
    void failedConditionalUpdateReturnsConflict() {
        when(providers.selectOne(any(Wrapper.class))).thenReturn(entity());
        assertConflict(() -> service.update(entity().getPublicId(), updateRequest()));
    }

    @Test
    void missingUpdateOrDeleteReturnsNotFound() {
        assertThatThrownBy(() -> service.update("missing", updateRequest()))
                .isInstanceOfSatisfying(BizException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
        assertThatThrownBy(() -> service.delete("missing")).isInstanceOf(BizException.class);
    }

    @Test
    void deleteWithModelsIsRejected() {
        when(providers.selectOne(any(Wrapper.class))).thenReturn(entity());
        when(models.exists(any(Wrapper.class))).thenReturn(true);
        assertConflict(() -> service.delete(entity().getPublicId()));
        verify(health, never()).deleteById(anyLong());
    }

    @Test
    void deleteRemovesHealthAndSoftDeletesProvider() {
        when(providers.selectOne(any(Wrapper.class))).thenReturn(entity());
        when(providers.deleteById(1L)).thenReturn(1);
        service.delete(entity().getPublicId());
        verify(providers).deleteById(1L);
        verify(health).deleteById(1L);
    }

    @Test
    void detailAlwaysReadsHealthAndDoesNotMutateCachedConfiguration() {
        var config = new ProviderConfigResp();
        config.setId(1L);
        config.setConfigVersion(2L);
        when(queries.get(entity().getPublicId())).thenReturn(config);
        var observation = new ProviderHealthDO();
        observation.setStatus("UP");
        observation.setProviderConfigVersion(1L);
        when(health.selectById(1L)).thenReturn(observation);
        assertThat(service.get(entity().getPublicId()).getProviderHealth().getStatus()).isEqualTo("UNKNOWN");
        observation.setProviderConfigVersion(2L);
        assertThat(service.get(entity().getPublicId()).getProviderHealth().getStatus()).isEqualTo("UP");
        verify(health, times(2)).selectById(1L);
        assertThat(observation.getStatus()).isEqualTo("UP");
    }

    @Test
    void validationRejectsInvalidProtocolUrlAndSensitiveExtraParams() {
        var req = createRequest();
        req.setType("azure_openai");
        assertThatThrownBy(() -> service.create(req)).isInstanceOf(BizException.class);
        req.setType("openai");
        req.setBaseUrl("https://user:password@example.com");
        assertThatThrownBy(() -> service.create(req)).isInstanceOf(BizException.class);
        req.setBaseUrl("http://ollama:11434");
        req.setExtraParams(Map.of("headers", Map.of("Authorization", "secret")));
        assertThatThrownBy(() -> service.create(req)).isInstanceOf(BizException.class);
    }

    @Test
    void updateWithoutEnabledDoesNotReactivateDisabledProvider() {
        var existing = entity();
        existing.setEnabled(false);
        when(providers.selectOne(any(Wrapper.class))).thenReturn(existing);
        when(providers.update(any(ModelProviderDO.class), any(Wrapper.class))).thenReturn(1);
        assertThat(service.update(existing.getPublicId(), updateRequest()).getEnabled()).isFalse();
    }

    @Test
    void protocolChangeCannotReusePreviousCredentials() {
        when(providers.selectOne(any(Wrapper.class))).thenReturn(entity());
        var req = updateRequest();
        req.setType("ollama");
        assertThatThrownBy(() -> service.update(entity().getPublicId(), req)).isInstanceOf(BizException.class);
        verify(providers, never()).update(any(ModelProviderDO.class), any(Wrapper.class));
    }

    @Test
    void missingHealthReturnsUnknownWithoutWriting() {
        var config = new ProviderConfigResp();
        config.setId(1L);
        config.setConfigVersion(1L);
        when(queries.get("provider")).thenReturn(config);
        assertThat(service.get("provider").getProviderHealth().getStatus()).isEqualTo("UNKNOWN");
        verify(health, never()).insert(any(ProviderHealthDO.class));
    }

    @Test
    void connectionTestLoadsCredentialsWithoutCachingOrOpeningTransaction() {
        var existing = entity();
        existing.setEnabled(false);
        when(providers.selectOne(any(Wrapper.class))).thenReturn(existing);
        var response = service.testConnection(existing.getPublicId());
        assertThat(response.getConnected()).isTrue();
        assertThat(response.getLatencyMs()).isNotNegative();
        verify(connectivity).testConnection(existing);
        var query = ArgumentCaptor.forClass(Wrapper.class);
        verify(providers).selectOne(query.capture());
        assertThat(query.getValue().getSqlSegment()).doesNotContain("FOR UPDATE");
        verifyNoInteractions(health, queries);
    }

    @Test
    void connectionTestReturnsMissingOrUpstreamFailureWithoutHealthWrites() {
        assertThatThrownBy(() -> service.testConnection("missing"))
                .isInstanceOfSatisfying(BizException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
        when(providers.selectOne(any(Wrapper.class))).thenReturn(entity());
        doThrow(new com.hify.shared.http.LlmApiException(com.hify.shared.http.LlmErrorKind.TIMEOUT))
                .when(connectivity).testConnection(any());
        assertThatThrownBy(() -> service.testConnection(entity().getPublicId()))
                .isInstanceOf(com.hify.shared.http.LlmApiException.class);
        verifyNoInteractions(health);
    }

    private static void assertConflict(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(BizException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.CONFLICT));
    }
}
