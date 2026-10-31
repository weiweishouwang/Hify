package com.hify.ai.internal.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hify.ai.api.ProviderConfigResp;
import com.hify.ai.api.ProviderListQuery;
import com.hify.ai.api.ProviderListResp;
import com.hify.ai.api.ProviderSummaryResp;
import com.hify.ai.internal.converter.ProviderConverter;
import com.hify.ai.internal.entity.ModelDO;
import com.hify.ai.internal.entity.ModelProviderDO;
import com.hify.ai.internal.mapper.ModelMapper;
import com.hify.ai.internal.mapper.ModelProviderMapper;
import com.hify.shared.config.CacheConfig;
import com.hify.shared.exception.BizException;
import com.hify.shared.exception.ErrorCode;
import com.hify.shared.web.PageQuery;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/** 独立缓存代理只保存配置，外层服务在每次详情查询时追加健康信息。 */
@Service
public class ProviderConfigQueryService {
    private static final int MAX_CURSOR_LENGTH = 512;
    private final ModelProviderMapper providers;
    private final ModelMapper models;

    public ProviderConfigQueryService(ModelProviderMapper providers, ModelMapper models) {
        this.providers = providers;
        this.models = models;
    }

    @Cacheable(cacheNames = CacheConfig.PROVIDER_CACHE,
            key = "'list:' + T(com.hify.ai.internal.service.ProviderConfigQueryService).cacheKey(#query)")
    public ProviderListResp list(ProviderListQuery query) {
        if (query == null) {
            throw ProviderConfigSupport.invalid("列表查询参数不能为空");
        }
        var wrapper = new QueryWrapper<ModelProviderDO>();
        if (query.getType() != null) {
            wrapper.eq("type", ProviderConfigSupport.type(query.getType()));
        }
        if (query.getEnabled() != null) {
            wrapper.eq("enabled", query.getEnabled());
        }
        if (query.getCursor() != null && !query.getCursor().isBlank()) {
            Cursor cursor = decodeCursor(query.getCursor());
            wrapper.apply("(created_at, id) < ({0}, {1})", cursor.createdAt(), cursor.id());
        }
        int size = size(query);
        wrapper.orderByDesc("created_at", "id").last("LIMIT " + (size + 1));
        List<ModelProviderDO> rows = providers.selectList(wrapper);
        boolean hasNext = rows.size() > size;
        List<ModelProviderDO> visible = rows.subList(0, Math.min(rows.size(), size));
        List<ProviderSummaryResp> items = new ArrayList<>();
        visible.forEach(row -> items.add(ProviderConverter.toSummary(row)));
        var result = new ProviderListResp();
        result.setItems(items);
        result.setHasNext(hasNext);
        if (hasNext) {
            result.setNextCursor(encodeCursor(visible.getLast()));
        }
        return result;
    }

    @Cacheable(cacheNames = CacheConfig.PROVIDER_CACHE, key = "'detail:' + #publicId")
    public ProviderConfigResp get(String publicId) {
        var provider = providers.selectOne(new QueryWrapper<ModelProviderDO>().eq("public_id", publicId));
        if (provider == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "模型接入点不存在");
        }
        List<ModelDO> related = models.selectList(new QueryWrapper<ModelDO>()
                .eq("provider_id", provider.getId()).orderByAsc("sort_order", "id"));
        return ProviderConverter.toConfig(provider, related);
    }

    /** 参数只含已限定类型及 Base64 游标；分隔符不会产生合法参数之间的碰撞。 */
    public static String cacheKey(ProviderListQuery query) {
        if (query == null) {
            return "null";
        }
        return query.getType() + ":" + query.getEnabled() + ":" + size(query) + ":"
                + (query.getCursor() == null || query.getCursor().isBlank() ? "" : query.getCursor());
    }

    private static int size(ProviderListQuery query) {
        return query.getSize() == null ? PageQuery.DEFAULT_SIZE
                : Math.max(1, Math.min(query.getSize(), PageQuery.MAX_SIZE_ADMIN));
    }

    private static String encodeCursor(ModelProviderDO row) {
        String value = row.getCreatedAt() + "|" + row.getId();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static Cursor decodeCursor(String value) {
        try {
            if (value.length() > MAX_CURSOR_LENGTH) {
                throw new IllegalArgumentException("游标过长");
            }
            String[] parts = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8)
                    .split("\\|", -1);
            if (parts.length != 2) {
                throw new IllegalArgumentException("游标不完整");
            }
            long id = Long.parseLong(parts[1]);
            if (id <= 0) {
                throw new IllegalArgumentException("游标主键无效");
            }
            return new Cursor(LocalDateTime.parse(parts[0]), id);
        } catch (IllegalArgumentException | DateTimeParseException exception) {
            throw new BizException(ErrorCode.PARAM_ERROR, "列表游标格式不合法", exception);
        }
    }

    private record Cursor(LocalDateTime createdAt, long id) { }
}
