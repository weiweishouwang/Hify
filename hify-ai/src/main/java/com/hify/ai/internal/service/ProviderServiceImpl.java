package com.hify.ai.internal.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.hify.ai.api.CredentialActionEnum;
import com.hify.ai.api.ProviderConfigResp;
import com.hify.ai.api.ProviderConnectionTestResp;
import com.hify.ai.api.ProviderCreateReq;
import com.hify.ai.api.ProviderDetailResp;
import com.hify.ai.api.ProviderListQuery;
import com.hify.ai.api.ProviderListResp;
import com.hify.ai.api.ProviderService;
import com.hify.ai.api.ProviderSummaryResp;
import com.hify.ai.api.ProviderUpdateReq;
import com.hify.ai.internal.converter.ProviderConverter;
import com.hify.ai.internal.entity.ModelDO;
import com.hify.ai.internal.entity.ModelProviderDO;
import com.hify.ai.internal.entity.ProviderHealthDO;
import com.hify.ai.internal.entity.ProviderModelCount;
import com.hify.ai.internal.mapper.ModelMapper;
import com.hify.ai.internal.mapper.ModelProviderMapper;
import com.hify.ai.internal.mapper.ProviderHealthMapper;
import com.hify.ai.internal.security.ProviderAuthConfig;
import com.hify.ai.internal.security.ProviderCredentialCipher;
import com.hify.ai.internal.spi.ProviderAdapterFactory;
import com.hify.shared.config.CacheConfig;
import com.hify.shared.exception.BizException;
import com.hify.shared.exception.ErrorCode;
import com.hify.shared.id.IdGenerator;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 配置 CRUD 与详情聚合；事务仅包数据库操作，不发起任何上游请求。 */
@Service
public class ProviderServiceImpl implements ProviderService {
    private final ModelProviderMapper providers;
    private final ModelMapper models;
    private final ProviderHealthMapper health;
    private final ProviderConfigQueryService queries;
    private final ProviderCredentialCipher cipher;
    private final ProviderAdapterFactory adapterFactory;

    public ProviderServiceImpl(ModelProviderMapper providers, ModelMapper models, ProviderHealthMapper health,
                               ProviderConfigQueryService queries, ProviderCredentialCipher cipher,
                               ProviderAdapterFactory adapterFactory) {
        this.providers = providers;
        this.models = models;
        this.health = health;
        this.queries = queries;
        this.cipher = cipher;
        this.adapterFactory = adapterFactory;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(cacheNames = CacheConfig.PROVIDER_CACHE, allEntries = true)
    public ProviderConfigResp create(ProviderCreateReq request) {
        requireRequest(request);
        var entity = ProviderConverter.newProvider(request);
        requireAvailableName(entity.getName(), null);
        entity.setPublicId(IdGenerator.nextUlid());
        entity.setConfigVersion(ProviderConfigSupport.INITIAL_VERSION);
        entity.setAuthConfig(ProviderAuthConfig.prepare(entity.getType(), request.getAuthConfig(), null,
                CredentialActionEnum.REPLACE, cipher));
        try {
            providers.insert(entity);
        } catch (DuplicateKeyException exception) {
            throw duplicate(exception);
        }
        var initialHealth = new ProviderHealthDO();
        initialHealth.setProviderId(entity.getId());
        initialHealth.setStatus(ProviderConfigSupport.UNKNOWN);
        initialHealth.setFailCount(0);
        health.insert(initialHealth);
        return ProviderConverter.toConfig(entity, List.of());
    }

    @Override
    public ProviderListResp list(ProviderListQuery query) {
        var cached = queries.list(query);
        var result = new ProviderListResp();
        result.setHasNext(cached.getHasNext());
        result.setNextCursor(cached.getNextCursor());
        if (cached.getItems().isEmpty()) {
            result.setItems(List.of());
            return result;
        }
        var ids = cached.getItems().stream().map(ProviderSummaryResp::getId).toList();
        var healthById = health.selectByIds(ids).stream().collect(Collectors.toMap(
                ProviderHealthDO::getProviderId, Function.identity()));
        var counts = models.countEnabledByProviderIds(ids).stream().collect(Collectors.toMap(
                ProviderModelCount::providerId,
                ProviderModelCount::enabledModelCount));
        var items = new ArrayList<ProviderSummaryResp>();
        for (var summary : cached.getItems()) {
            items.add(ProviderConverter.toListItem(summary, healthById.get(summary.getId()),
                    counts.getOrDefault(summary.getId(), 0L)));
        }
        result.setItems(items);
        return result;
    }

    @Override
    public ProviderDetailResp get(String publicId) {
        requirePublicId(publicId);
        var config = queries.get(publicId);
        var result = new ProviderDetailResp();
        result.setProvider(config);
        result.setProviderHealth(ProviderConverter.toHealth(health.selectById(config.getId()), config.getConfigVersion()));
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(cacheNames = CacheConfig.PROVIDER_CACHE, allEntries = true)
    public ProviderConfigResp update(String publicId, ProviderUpdateReq request) {
        requireRequest(request);
        var existing = requireExisting(publicId);
        if (request.getConfigVersion() == null
                || !Objects.equals(existing.getConfigVersion(), request.getConfigVersion())) {
            throw new BizException(ErrorCode.CONFLICT, "接入点配置已变更，请刷新后重试");
        }
        var target = ProviderConverter.newProvider(request);
        if (request.getEnabled() == null) {
            target.setEnabled(existing.getEnabled());
        }
        requireAvailableName(target.getName(), existing.getId());
        if (!Objects.equals(target.getType(), existing.getType())
                && request.getCredentialAction() == CredentialActionEnum.KEEP) {
            throw ProviderConfigSupport.invalid("切换协议时必须明确替换或清除鉴权配置");
        }
        target.setId(existing.getId());
        target.setPublicId(existing.getPublicId());
        target.setCreatedAt(existing.getCreatedAt());
        target.setCreatedBy(existing.getCreatedBy());
        target.setConfigVersion(existing.getConfigVersion() + 1);
        target.setAuthConfig(ProviderAuthConfig.prepare(target.getType(), request.getAuthConfig(),
                Objects.equals(target.getType(), existing.getType()) ? existing.getAuthConfig() : null,
                request.getCredentialAction(), cipher));
        try {
            int changed = providers.update(target, new UpdateWrapper<ModelProviderDO>()
                    .eq("id", existing.getId()).eq("config_version", request.getConfigVersion()));
            if (changed == 0) {
                throw new BizException(ErrorCode.CONFLICT, "接入点配置已变更，请刷新后重试");
            }
        } catch (DuplicateKeyException exception) {
            throw duplicate(exception);
        }
        return ProviderConverter.toConfig(target, models.selectList(new QueryWrapper<ModelDO>()
                .eq("provider_id", existing.getId()).orderByAsc("sort_order", "id")));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(cacheNames = CacheConfig.PROVIDER_CACHE, allEntries = true)
    public void delete(String publicId) {
        var existing = requireExisting(publicId);
        if (models.exists(new QueryWrapper<ModelDO>().eq("provider_id", existing.getId()))) {
            throw new BizException(ErrorCode.CONFLICT, "请先删除该接入点下的模型");
        }
        health.deleteById(existing.getId());
        if (providers.deleteById(existing.getId()) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "模型接入点不存在");
        }
    }

    /** 手动测试允许检查停用配置；不持有数据库事务、不改变定时探测的失败计数。 */
    @Override
    public ProviderConnectionTestResp testConnection(String publicId) {
        var provider = requireProvider(publicId);
        long start = System.nanoTime();
        adapterFactory.getAdapter(provider.getType()).testConnection(provider);
        var response = new ProviderConnectionTestResp();
        response.setConnected(true);
        response.setLatencyMs(java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
        return response;
    }

    @Override
    public List<String> listUpstreamModels(String publicId) {
        var provider = requireProvider(publicId);
        return adapterFactory.getAdapter(provider.getType()).listModels(provider);
    }

    private ModelProviderDO requireProvider(String publicId) {
        requirePublicId(publicId);
        var provider = providers.selectOne(new QueryWrapper<ModelProviderDO>().eq("public_id", publicId));
        if (provider == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "模型接入点不存在");
        }
        return provider;
    }

    private ModelProviderDO requireExisting(String publicId) {
        requirePublicId(publicId);
        var entity = providers.selectOne(new QueryWrapper<ModelProviderDO>()
                .eq("public_id", publicId).last("FOR UPDATE"));
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "模型接入点不存在");
        }
        return entity;
    }

    private void requireAvailableName(String name, Long currentId) {
        Long ownerId = providers.findIdByName(name);
        if (ownerId != null && !ownerId.equals(currentId)) {
            throw new BizException(ErrorCode.CONFLICT, "接入点名称已被占用（包括已删除记录）");
        }
    }

    private static void requirePublicId(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            throw ProviderConfigSupport.invalid("接入点标识不能为空");
        }
    }

    private static void requireRequest(ProviderCreateReq request) {
        if (request == null) {
            throw ProviderConfigSupport.invalid("接入点配置不能为空");
        }
    }

    private static BizException duplicate(DuplicateKeyException cause) {
        return new BizException(ErrorCode.CONFLICT, "接入点唯一标识或名称已被占用", cause);
    }
}
