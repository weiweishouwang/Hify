package com.hify.ai.internal.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.hify.ai.api.ModelListQuery;
import com.hify.ai.api.ModelListResp;
import com.hify.ai.api.ModelSummaryResp;
import com.hify.ai.api.ModelSyncResp;
import com.hify.ai.api.ModelUpdateReq;
import com.hify.ai.api.ModelService;
import com.hify.ai.internal.entity.ModelDO;
import com.hify.ai.internal.entity.ModelProviderDO;
import com.hify.ai.internal.mapper.ModelMapper;
import com.hify.ai.internal.mapper.ModelProviderMapper;
import com.hify.ai.internal.spi.ProviderAdapterFactory;
import com.hify.shared.exception.BizException;
import com.hify.shared.exception.ErrorCode;
import com.hify.shared.id.IdGenerator;
import com.hify.shared.mybatis.CursorCodec;
import com.hify.shared.web.PageQuery;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 模型管理。
 *
 * <p>模型行来自「从接入点同步上游目录」—— 上游目录接口不标模型用途，
 * {@code chat / embedding} 由用户在管理台人工认定。
 *
 * <p>事务只包数据库操作，不发起上游请求（CLAUDE.md §3.1 E5）。
 */
@Service
public class ModelServiceImpl implements ModelService {

    /** 默认模型用途：上游不标类型，先按 chat 落，由用户改成 embedding。 */
    private static final String DEFAULT_MODEL_TYPE = "chat";

    /** 初始配置版本。 */
    private static final long INITIAL_VERSION = 1L;

    private final ModelMapper models;
    private final ModelProviderMapper providers;
    private final ProviderAdapterFactory adapterFactory;

    public ModelServiceImpl(ModelMapper models, ModelProviderMapper providers,
                            ProviderAdapterFactory adapterFactory) {
        this.models = models;
        this.providers = providers;
        this.adapterFactory = adapterFactory;
    }

    @Override
    public ModelListResp list(ModelListQuery query) {
        ModelListQuery effective = query == null ? new ModelListQuery() : query;
        var wrapper = new QueryWrapper<ModelDO>();
        if (effective.getProviderPublicId() != null && !effective.getProviderPublicId().isBlank()) {
            wrapper.eq("provider_id", requireProvider(effective.getProviderPublicId()).getId());
        }
        if (effective.getModelType() != null && !effective.getModelType().isBlank()) {
            wrapper.eq("model_type", effective.getModelType());
        }
        if (effective.getEnabled() != null) {
            wrapper.eq("enabled", effective.getEnabled());
        }
        String keyword = effective.getKeyword() == null ? null : effective.getKeyword().trim();
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(item -> item.like("display_name", keyword).or().like("model_name", keyword));
        }
        if (effective.getCursor() != null && !effective.getCursor().isBlank()) {
            var cursor = CursorCodec.decode(effective.getCursor());
            wrapper.apply("(created_at, id) < ({0}, {1})", cursor.createdAt(), cursor.id());
        }
        int size = size(effective);
        wrapper.orderByDesc("created_at", "id").last("LIMIT " + (size + 1));

        List<ModelDO> rows = models.selectList(wrapper);
        boolean hasNext = rows.size() > size;
        List<ModelDO> visible = rows.subList(0, Math.min(rows.size(), size));

        Map<Long, ModelProviderDO> providerById = loadProviders(visible);
        List<ModelSummaryResp> items = new ArrayList<>();
        for (ModelDO row : visible) {
            items.add(toSummary(row, providerById.get(row.getProviderId())));
        }

        ModelListResp result = new ModelListResp();
        result.setItems(items);
        result.setHasNext(hasNext);
        if (hasNext) {
            ModelDO last = visible.getLast();
            result.setNextCursor(CursorCodec.encode(last.getCreatedAt(), last.getId()));
        }
        return result;
    }

    @Override
    public ModelSummaryResp get(String publicId) {
        ModelDO model = requireExisting(publicId);
        return toSummary(model, providers.selectById(model.getProviderId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ModelSummaryResp update(String publicId, ModelUpdateReq request) {
        if (request == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "模型配置不能为空");
        }
        ModelDO existing = requireExisting(publicId);
        if (request.getConfigVersion() == null
                || !Objects.equals(existing.getConfigVersion(), request.getConfigVersion())) {
            throw new BizException(ErrorCode.CONFLICT, "模型配置已变更，请刷新后重试");
        }
        ModelDO target = new ModelDO();
        target.setId(existing.getId());
        target.setPublicId(existing.getPublicId());
        target.setProviderId(existing.getProviderId());
        target.setModelName(existing.getModelName());
        target.setDisplayName(request.getDisplayName() == null ? existing.getDisplayName() : request.getDisplayName());
        target.setModelType(request.getModelType() == null ? existing.getModelType() : request.getModelType());
        target.setCapabilities(request.getCapabilities() == null ? existing.getCapabilities() : request.getCapabilities());
        target.setContextWindow(request.getContextWindow() == null
                ? existing.getContextWindow() : request.getContextWindow());
        target.setMaxOutputTokens(request.getMaxOutputTokens() == null
                ? existing.getMaxOutputTokens() : request.getMaxOutputTokens());
        target.setEmbeddingDimension(request.getEmbeddingDimension() == null
                ? existing.getEmbeddingDimension() : request.getEmbeddingDimension());
        target.setEnabled(request.getEnabled() == null ? existing.getEnabled() : request.getEnabled());
        target.setSortOrder(request.getSortOrder() == null ? existing.getSortOrder() : request.getSortOrder());
        target.setConfigVersion(existing.getConfigVersion() + 1);
        try {
            int changed = models.update(target, new UpdateWrapper<ModelDO>()
                    .eq("id", existing.getId()).eq("config_version", request.getConfigVersion()));
            if (changed == 0) {
                throw new BizException(ErrorCode.CONFLICT, "模型配置已变更，请刷新后重试");
            }
        } catch (DuplicateKeyException exception) {
            throw new BizException(ErrorCode.CONFLICT, "模型唯一标识已被占用", exception);
        }
        return toSummary(target, providers.selectById(existing.getProviderId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String publicId) {
        ModelDO existing = requireExisting(publicId);
        if (models.deleteById(existing.getId()) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "模型不存在");
        }
    }

    @Override
    public ModelSyncResp sync(String providerPublicId) {
        ModelProviderDO provider = requireProvider(providerPublicId);
        List<String> identifiers = adapterFactory.getAdapter(provider.getType()).listModels(provider);

        int created = 0;
        int updated = 0;
        Set<String> seen = new HashSet<>();
        for (String identifier : identifiers) {
            if (identifier == null || identifier.isBlank() || !seen.add(identifier)) {
                continue;
            }
            Long existingId = models.findIdByProviderAndModel(provider.getId(), identifier);
            if (existingId == null) {
                ModelDO entity = new ModelDO();
                entity.setPublicId(IdGenerator.nextUlid());
                entity.setProviderId(provider.getId());
                entity.setModelName(identifier);
                entity.setDisplayName(identifier);
                entity.setModelType(DEFAULT_MODEL_TYPE);
                entity.setEnabled(true);
                entity.setSortOrder(0);
                entity.setConfigVersion(INITIAL_VERSION);
                try {
                    models.insert(entity);
                    created++;
                } catch (DuplicateKeyException exception) {
                    // 并发同步撞上唯一键：另一路已经建好，不算失败
                    updated++;
                }
            } else {
                ModelDO existing = models.selectById(existingId);
                if (existing != null && !Boolean.TRUE.equals(existing.getEnabled())) {
                    existing.setEnabled(true);
                    models.updateById(existing);
                    updated++;
                }
            }
        }
        int disabled = disableVanished(provider.getId(), seen);

        ModelSyncResp result = new ModelSyncResp();
        result.setDiscovered(seen.size());
        result.setCreated(created);
        result.setUpdated(updated);
        result.setDisabled(disabled);
        return result;
    }

    // ── 内部方法 ────────────────────────────────────────────────────

    /** 上游不再提供的模型置为停用；不物理删除 —— 可能已被 Agent 引用。 */
    private int disableVanished(Long providerId, Set<String> seen) {
        int disabled = 0;
        for (String modelName : models.listModelNamesByProvider(providerId)) {
            if (seen.contains(modelName)) {
                continue;
            }
            ModelDO row = models.selectOne(new QueryWrapper<ModelDO>()
                    .eq("provider_id", providerId).eq("model_name", modelName));
            if (row == null || !Boolean.TRUE.equals(row.getEnabled())) {
                continue;
            }
            row.setEnabled(false);
            models.updateById(row);
            disabled++;
        }
        return disabled;
    }

    private Map<Long, ModelProviderDO> loadProviders(List<ModelDO> rows) {
        Set<Long> providerIds = rows.stream().map(ModelDO::getProviderId).collect(Collectors.toSet());
        if (providerIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, ModelProviderDO> byId = providers.selectBatchIds(providerIds).stream()
                .collect(Collectors.toMap(ModelProviderDO::getId, Function.identity(), (left, right) -> left));
        return new LinkedHashMap<>(byId);
    }

    private ModelSummaryResp toSummary(ModelDO row, ModelProviderDO provider) {
        ModelSummaryResp resp = new ModelSummaryResp();
        resp.setId(row.getId());
        resp.setPublicId(row.getPublicId());
        resp.setProviderPublicId(provider == null ? null : provider.getPublicId());
        resp.setProviderName(provider == null ? null : provider.getName());
        resp.setModelName(row.getModelName());
        resp.setDisplayName(row.getDisplayName());
        resp.setModelType(row.getModelType());
        resp.setCapabilities(row.getCapabilities());
        resp.setContextWindow(row.getContextWindow());
        resp.setMaxOutputTokens(row.getMaxOutputTokens());
        resp.setEmbeddingDimension(row.getEmbeddingDimension());
        resp.setEnabled(row.getEnabled());
        resp.setSortOrder(row.getSortOrder());
        resp.setConfigVersion(row.getConfigVersion());
        resp.setCreatedAt(row.getCreatedAt());
        resp.setUpdatedAt(row.getUpdatedAt());
        return resp;
    }

    private ModelDO requireExisting(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "模型标识不能为空");
        }
        ModelDO entity = models.selectOne(new QueryWrapper<ModelDO>().eq("public_id", publicId));
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "模型不存在");
        }
        return entity;
    }

    private ModelProviderDO requireProvider(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "接入点标识不能为空");
        }
        ModelProviderDO provider = providers.selectOne(new QueryWrapper<ModelProviderDO>().eq("public_id", publicId));
        if (provider == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "模型接入点不存在");
        }
        return provider;
    }

    private static int size(ModelListQuery query) {
        if (query.getSize() == null) {
            return PageQuery.DEFAULT_SIZE;
        }
        return Math.max(1, Math.min(query.getSize(), PageQuery.MAX_SIZE_ADMIN));
    }
}
