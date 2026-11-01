package com.hify.ai.internal.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hify.ai.api.EmbeddingService;
import com.hify.ai.internal.entity.ModelDO;
import com.hify.ai.internal.entity.ModelProviderDO;
import com.hify.ai.internal.mapper.ModelMapper;
import com.hify.ai.internal.mapper.ModelProviderMapper;
import com.hify.ai.internal.spi.ProviderAdapterFactory;
import com.hify.shared.exception.BizException;
import com.hify.shared.exception.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 向量化的实现。
 *
 * <p><b>纯能力层</b>：只做「给我文本，返回向量」。不检索、不拼上下文 ——
 * 那些是 knowledge 与编排层的事。
 *
 * <p>调用是阻塞的，调用方要放在专用线程上跑，不要占 Tomcat 工作线程。
 */
@Service
public class EmbeddingServiceImpl implements EmbeddingService {

    private final ModelMapper models;
    private final ModelProviderMapper providers;
    private final ProviderAdapterFactory adapterFactory;

    public EmbeddingServiceImpl(ModelMapper models, ModelProviderMapper providers,
                                ProviderAdapterFactory adapterFactory) {
        this.models = models;
        this.providers = providers;
        this.adapterFactory = adapterFactory;
    }

    @Override
    public List<float[]> embed(String modelPublicId, List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        if (modelPublicId == null || modelPublicId.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "模型标识不能为空");
        }
        ModelDO model = models.selectOne(new QueryWrapper<ModelDO>().eq("public_id", modelPublicId));
        if (model == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "模型不存在");
        }
        if (!"embedding".equals(model.getModelType())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "该模型不是 embedding 模型");
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
        return adapterFactory.getAdapter(provider.getType()).embed(provider, model.getModelName(), texts);
    }
}
