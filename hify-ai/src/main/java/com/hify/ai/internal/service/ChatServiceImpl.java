package com.hify.ai.internal.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hify.ai.api.ChatListener;
import com.hify.ai.api.ChatRequest;
import com.hify.ai.api.ChatService;
import com.hify.ai.internal.entity.ModelDO;
import com.hify.ai.internal.entity.ModelProviderDO;
import com.hify.ai.internal.mapper.ModelMapper;
import com.hify.ai.internal.mapper.ModelProviderMapper;
import com.hify.ai.internal.spi.ChatAdapterFactory;
import com.hify.shared.exception.BizException;
import com.hify.shared.exception.ErrorCode;
import org.springframework.stereotype.Service;

/**
 * 流式对话的实现。
 *
 * <p><b>纯能力层</b>：把消息发给模型，把增量吐回来。
 * 不检索、不拼上下文、不执行工具、不做工具循环 —— 那些是编排层的事
 * （CLAUDE.md §2.1 的模块不变量）。
 *
 * <p><b>调用是阻塞的</b>：要等上游把整条流吐完才返回。调用方要放在
 * {@code llmExecutor} 上跑，不要占 Tomcat 工作线程（CLAUDE.md §3.5）。
 */
@Service
public class ChatServiceImpl implements ChatService {

    private final ModelMapper models;
    private final ModelProviderMapper providers;
    private final ChatAdapterFactory adapterFactory;

    public ChatServiceImpl(ModelMapper models, ModelProviderMapper providers, ChatAdapterFactory adapterFactory) {
        this.models = models;
        this.providers = providers;
        this.adapterFactory = adapterFactory;
    }

    @Override
    public void stream(ChatRequest request, ChatListener listener) {
        if (request == null || request.getModelPublicId() == null || request.getModelPublicId().isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "模型标识不能为空");
        }
        if (request.getMessages() == null || request.getMessages().isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "对话消息不能为空");
        }
        ModelDO model = models.selectOne(new QueryWrapper<ModelDO>()
                .eq("public_id", request.getModelPublicId().trim()));
        if (model == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "模型不存在");
        }
        if (!"chat".equals(model.getModelType())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "该模型不是对话模型");
        }
        if (!Boolean.TRUE.equals(model.getEnabled())) {
            throw new BizException(ErrorCode.CONFLICT, "模型已停用");
        }
        ModelProviderDO provider = providers.selectById(model.getProviderId());
        if (provider == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "接入点不存在");
        }
        if (!Boolean.TRUE.equals(provider.getEnabled())) {
            throw new BizException(ErrorCode.CONFLICT, "接入点已停用");
        }
        adapterFactory.getAdapter(provider.getType()).stream(provider, model.getModelName(), request, listener);
    }
}
