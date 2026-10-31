package com.hify.ai.internal.controller;

import com.hify.ai.api.ModelConfigResp;
import com.hify.ai.api.ProviderConfigResp;
import com.hify.ai.api.ProviderConnectionTestResp;
import com.hify.ai.api.ProviderCreateReq;
import com.hify.ai.api.ProviderDetailResp;
import com.hify.ai.api.ProviderHealthResp;
import com.hify.ai.api.ProviderListQuery;
import com.hify.ai.api.ProviderListResp;
import com.hify.ai.api.ProviderService;
import com.hify.ai.api.ProviderUpdateReq;
import com.hify.shared.config.ThreadPoolConfig;
import com.hify.shared.exception.BizException;
import com.hify.shared.exception.ErrorCode;
import com.hify.shared.http.LlmApiException;
import com.hify.shared.http.LlmErrorKind;
import com.hify.shared.web.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 真正经过 Spring MVC 参数绑定、校验、异步派发与统一异常处理，不访问外部系统。 */
class ProviderControllerTest {
    private static final String ID = "01ARZ3NDEKTSV4RRFFQ69G5FAV";
    private static final String ROOT = "/api/v1/providers";
    private static final String CREATE = """
            {"name":"OpenAI","type":"openai","baseUrl":"https://api.openai.com/v1"}
            """;
    private static final String UPDATE = """
            {"name":"OpenAI","type":"openai","baseUrl":"https://api.openai.com/v1","configVersion":1}
            """;
    ProviderService service;
    ThreadPoolExecutor executor;
    LocalValidatorFactoryBean validator;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(ProviderService.class);
        executor = new ThreadPoolConfig().llmExecutor();
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(new ProviderController(service,
                        new ProviderConnectionTestDispatcher(service, executor)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator).build();
    }

    @AfterEach
    void close() {
        executor.shutdownNow();
        validator.close();
    }

    @Test
    void createReturnsOneResultEnvelopeAndBindsBody() throws Exception {
        var response = new ProviderConfigResp();
        response.setPublicId(ID);
        response.setCredentialsConfigured(false);
        when(service.create(any())).thenReturn(response);
        mvc.perform(post(ROOT).contentType(MediaType.APPLICATION_JSON).content(CREATE))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.publicId").value(ID))
                .andExpect(jsonPath("$.data.code").doesNotExist())
                .andExpect(jsonPath("$.data.authConfig.apiKey").doesNotExist());
        var request = ArgumentCaptor.forClass(ProviderCreateReq.class);
        verify(service).create(request.capture());
        assertThat(request.getValue().getName()).isEqualTo("OpenAI");
    }

    @Test
    void listBindsFiltersAndCursorPaginationWithoutNestedResult() throws Exception {
        var page = new ProviderListResp();
        page.setItems(List.of());
        page.setHasNext(true);
        page.setNextCursor("next-cursor");
        when(service.list(any())).thenReturn(page);
        mvc.perform(get(ROOT).param("type", "ollama").param("enabled", "false")
                        .param("size", "10").param("cursor", "previous-cursor"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.hasNext").value(true))
                .andExpect(jsonPath("$.data.nextCursor").value("next-cursor"))
                .andExpect(jsonPath("$.data.code").doesNotExist());
        var query = ArgumentCaptor.forClass(ProviderListQuery.class);
        verify(service).list(query.capture());
        assertThat(query.getValue().getType()).isEqualTo("ollama");
        assertThat(query.getValue().getEnabled()).isFalse();
        assertThat(query.getValue().getSize()).isEqualTo(10);
        assertThat(query.getValue().getCursor()).isEqualTo("previous-cursor");
    }

    @Test
    void detailIncludesModelsAndHealth() throws Exception {
        var config = new ProviderConfigResp();
        config.setPublicId(ID);
        config.setAuthConfig(Map.of());
        var model = new ModelConfigResp();
        model.setModelName("upstream-model");
        config.setModelConfigs(List.of(model));
        var health = new ProviderHealthResp();
        health.setStatus("UP");
        var detail = new ProviderDetailResp();
        detail.setProvider(config);
        detail.setProviderHealth(health);
        when(service.get(ID)).thenReturn(detail);
        mvc.perform(get(ROOT + "/" + ID)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.provider.modelConfigs[0].modelName").value("upstream-model"))
                .andExpect(jsonPath("$.data.providerHealth.status").value("UP"));
    }

    @Test
    void updateBindsVersionAndDeleteReturnsEmptySuccess() throws Exception {
        when(service.update(eq(ID), any())).thenReturn(new ProviderConfigResp());
        mvc.perform(put(ROOT + "/" + ID).contentType(MediaType.APPLICATION_JSON).content(UPDATE))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        var request = ArgumentCaptor.forClass(ProviderUpdateReq.class);
        verify(service).update(eq(ID), request.capture());
        assertThat(request.getValue().getConfigVersion()).isEqualTo(1L);
        mvc.perform(delete(ROOT + "/" + ID)).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0)).andExpect(jsonPath("$.data").isEmpty());
        verify(service).delete(ID);
    }

    @Test
    void invalidBodiesAreRejectedBeforeService() throws Exception {
        for (String body : new String[]{"{}", CREATE.replace("OpenAI", " "),
                CREATE.replace("openai\"", "unsupported\""), CREATE.replace("https://api.openai.com/v1", "")}) {
            mvc.perform(post(ROOT).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR.getCode()));
        }
        mvc.perform(put(ROOT + "/" + ID).contentType(MediaType.APPLICATION_JSON).content(CREATE))
                .andExpect(status().isBadRequest());
        mvc.perform(put(ROOT + "/" + ID).contentType(MediaType.APPLICATION_JSON)
                        .content(UPDATE.replace("\"configVersion\":1", "\"configVersion\":0")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void invalidQueryParametersReturnResultErrors() throws Exception {
        for (String size : new String[]{"0", "201", "abc"}) {
            mvc.perform(get(ROOT).param("size", size)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR.getCode()));
        }
        mvc.perform(get(ROOT).param("type", "unsupported")).andExpect(status().isBadRequest());
        mvc.perform(get(ROOT).param("enabled", "not-boolean")).andExpect(status().isBadRequest());
        mvc.perform(get(ROOT).param("cursor", "x".repeat(513))).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void malformedBodyAndInvalidPublicIdReturnResultErrors() throws Exception {
        mvc.perform(post(ROOT).contentType(MediaType.APPLICATION_JSON).content("{bad-json"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.data").isEmpty());
        mvc.perform(get(ROOT + "/not-a-ulid")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR.getCode()));
        mvc.perform(delete(ROOT + "/not-a-ulid")).andExpect(status().isBadRequest());
        mvc.perform(post(ROOT + "/not-a-ulid/test-connection")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void businessErrorsPreserveHttpStatusAndErrorCode() throws Exception {
        when(service.get(ID)).thenThrow(new BizException(ErrorCode.NOT_FOUND, "接入点不存在"));
        mvc.perform(get(ROOT + "/" + ID)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.NOT_FOUND.getCode()));
        when(service.create(any())).thenThrow(new BizException(ErrorCode.CONFLICT, "名称重复"));
        mvc.perform(post(ROOT).contentType(MediaType.APPLICATION_JSON).content(CREATE))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(ErrorCode.CONFLICT.getCode()));
    }

    @Test
    void testConnectionUsesLlmWorkerAndReturnsResultAsynchronously() throws Exception {
        Thread caller = Thread.currentThread();
        AtomicReference<Thread> worker = new AtomicReference<>();
        when(service.testConnection(ID)).thenAnswer(invocation -> {
            worker.set(Thread.currentThread());
            var response = new ProviderConnectionTestResp();
            response.setConnected(true);
            response.setLatencyMs(12L);
            return response;
        });
        var pending = mvc.perform(post(ROOT + "/" + ID + "/test-connection"))
                .andExpect(request().asyncStarted()).andReturn();
        mvc.perform(asyncDispatch(pending)).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.connected").value(true))
                .andExpect(jsonPath("$.data.latencyMs").value(12));
        assertThat(worker.get()).isNotEqualTo(caller);
        assertThat(worker.get().getName()).startsWith("llm-");
    }

    @Test
    void invalidSensitiveAuthConfigDoesNotLeakIntoParserLogs() throws Exception {
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            mvc.perform(post(ROOT).contentType(MediaType.APPLICATION_JSON).content("""
                    {"name":"test","type":"openai","baseUrl":"https://example.com",
                     "authConfig":"sensitive-key-must-not-be-logged"}
                    """))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR.getCode()));
            assertThat(appender.list).isNotEmpty().allSatisfy(event ->
                    assertThat(event.getFormattedMessage()).doesNotContain("sensitive-key-must-not-be-logged"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void asynchronousConnectionFailureUsesGlobalErrorEnvelope() throws Exception {
        when(service.testConnection(ID)).thenThrow(new LlmApiException(LlmErrorKind.AUTH_FAILED));
        var pending = mvc.perform(post(ROOT + "/" + ID + "/test-connection"))
                .andExpect(request().asyncStarted()).andReturn();
        mvc.perform(asyncDispatch(pending)).andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value(ErrorCode.UPSTREAM_ERROR.getCode()))
                .andExpect(jsonPath("$.data").isEmpty());
    }
}
