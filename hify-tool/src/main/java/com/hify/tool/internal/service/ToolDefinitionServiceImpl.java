package com.hify.tool.internal.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.hify.shared.exception.BizException;
import com.hify.shared.exception.ErrorCode;
import com.hify.shared.id.IdGenerator;
import com.hify.tool.api.ToolCreateReq;
import com.hify.tool.api.ToolDetailResp;
import com.hify.tool.api.ToolListQuery;
import com.hify.tool.api.ToolListResp;
import com.hify.tool.api.ToolSourceType;
import com.hify.tool.api.ToolSummaryResp;
import com.hify.tool.api.ToolSyncResp;
import com.hify.tool.api.ToolUpdateReq;
import com.hify.tool.internal.converter.ToolConverter;
import com.hify.tool.internal.entity.ToolDefinitionDO;
import com.hify.tool.internal.mapper.ToolDefinitionMapper;
import com.hify.tool.internal.security.ToolHeaderCipher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 工具定义的增删改查。
 *
 * <p>事务只包数据库操作，不发起任何上游请求（CLAUDE.md §3.1 E5）。
 * 工具目录不做缓存 —— 工具数量少、变更频繁，缓存带来的陈旧问题比省下的那点开销更贵。
 */
@Service
public class ToolDefinitionServiceImpl implements com.hify.tool.api.ToolDefinitionService {

    /** 游标最大长度，防止超长串进 Base64 解码。 */
    private static final int MAX_CURSOR_LENGTH = 512;

    /** 初始配置版本。 */
    private static final long INITIAL_VERSION = 1L;

    private final ToolDefinitionMapper tools;
    private final ToolConverter converter;
    private final ToolHeaderCipher headerCipher;
    private final ToolSyncService syncService;

    public ToolDefinitionServiceImpl(ToolDefinitionMapper tools, ToolConverter converter,
                                     ToolHeaderCipher headerCipher, ToolSyncService syncService) {
        this.tools = tools;
        this.converter = converter;
        this.headerCipher = headerCipher;
        this.syncService = syncService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ToolDetailResp create(ToolCreateReq request) {
        if (request == null) {
            throw ToolSupport.invalid("工具配置不能为空");
        }
        String sourceType = ToolSourceType.HTTP.code();
        if (!sourceType.equals(request.getSourceType())) {
            throw ToolSupport.invalid("当前只支持手工创建 http 来源的工具");
        }
        String name = ToolSupport.name(request.getName());
        String toolKey = ToolSupport.toolKey(request.getToolKey());
        requireAvailableToolKey(sourceType, toolKey, null);
        requireAvailableName(name, null);

        ToolDefinitionDO entity = new ToolDefinitionDO();
        entity.setPublicId(IdGenerator.nextUlid());
        entity.setSourceType(sourceType);
        entity.setToolKey(toolKey);
        entity.setName(name);
        entity.setDescription(request.getDescription());
        entity.setSchema(request.getSchema());
        entity.setConfig(buildHttpConfig(request.getUrl(), request.getMethod(), request.getTimeoutMs(),
                null, request.getHeaders()));
        entity.setEnabled(true);
        entity.setConfigVersion(INITIAL_VERSION);
        try {
            tools.insert(entity);
        } catch (DuplicateKeyException exception) {
            throw duplicate(exception);
        }
        return converter.toDetail(entity);
    }

    @Override
    public ToolListResp list(ToolListQuery query) {
        ToolListQuery effective = query == null ? new ToolListQuery() : query;
        var wrapper = new QueryWrapper<ToolDefinitionDO>();
        if (effective.getSourceType() != null && !effective.getSourceType().isBlank()) {
            wrapper.eq("source_type", effective.getSourceType());
        }
        if (effective.getEnabled() != null) {
            wrapper.eq("enabled", effective.getEnabled());
        }
        String keyword = effective.getKeyword() == null ? null : effective.getKeyword().trim();
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(item -> item.like("name", keyword).or().like("tool_key", keyword));
        }
        if (effective.getCursor() != null && !effective.getCursor().isBlank()) {
            Cursor cursor = decodeCursor(effective.getCursor());
            wrapper.apply("(created_at, id) < ({0}, {1})", cursor.createdAt(), cursor.id());
        }
        int size = size(effective);
        wrapper.orderByDesc("created_at", "id").last("LIMIT " + (size + 1));

        List<ToolDefinitionDO> rows = tools.selectList(wrapper);
        boolean hasNext = rows.size() > size;
        List<ToolDefinitionDO> visible = rows.subList(0, Math.min(rows.size(), size));
        List<ToolSummaryResp> items = new ArrayList<>();
        visible.forEach(row -> items.add(converter.toSummary(row)));

        ToolListResp result = new ToolListResp();
        result.setItems(items);
        result.setHasNext(hasNext);
        if (hasNext) {
            result.setNextCursor(encodeCursor(visible.getLast()));
        }
        return result;
    }

    @Override
    public ToolDetailResp get(String publicId) {
        return converter.toDetail(requireExisting(publicId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ToolDetailResp update(String publicId, ToolUpdateReq request) {
        if (request == null) {
            throw ToolSupport.invalid("工具配置不能为空");
        }
        ToolDefinitionDO existing = requireExisting(publicId);
        if (request.getConfigVersion() == null
                || !Objects.equals(existing.getConfigVersion(), request.getConfigVersion())) {
            throw new BizException(ErrorCode.CONFLICT, "工具配置已变更，请刷新后重试");
        }
        if (!ToolSourceType.HTTP.code().equals(existing.getSourceType())) {
            throw new BizException(ErrorCode.CONFLICT, "内置与 MCP 来源的工具由同步机制维护，不能手工修改");
        }

        String name = request.getName() == null ? existing.getName() : ToolSupport.name(request.getName());
        requireAvailableName(name, existing.getId());

        Map<String, Object> existingConfig = existing.getConfig() == null ? Map.of() : existing.getConfig();
        Object storedHeaders = existingConfig.get(ToolConfig.HEADERS);
        Map<String, Object> storedHeaderMap = storedHeaders instanceof Map<?, ?> map ? castMap(map) : Map.of();

        ToolDefinitionDO target = new ToolDefinitionDO();
        target.setId(existing.getId());
        target.setPublicId(existing.getPublicId());
        target.setSourceType(existing.getSourceType());
        target.setToolKey(existing.getToolKey());
        target.setName(name);
        target.setDescription(request.getDescription() == null
                ? existing.getDescription() : request.getDescription());
        target.setSchema(request.getSchema() == null ? existing.getSchema() : request.getSchema());
        target.setEnabled(request.getEnabled() == null ? existing.getEnabled() : request.getEnabled());
        target.setConfigVersion(existing.getConfigVersion() + 1);
        target.setConfig(buildHttpConfig(
                request.getUrl() == null ? string(existingConfig.get(ToolConfig.URL)) : request.getUrl(),
                request.getMethod() == null ? string(existingConfig.get(ToolConfig.METHOD)) : request.getMethod(),
                request.getTimeoutMs() == null ? integer(existingConfig.get(ToolConfig.TIMEOUT_MS)) : request.getTimeoutMs(),
                storedHeaderMap,
                request.getHeaders()));

        try {
            int changed = tools.update(target, new UpdateWrapper<ToolDefinitionDO>()
                    .eq("id", existing.getId()).eq("config_version", request.getConfigVersion()));
            if (changed == 0) {
                throw new BizException(ErrorCode.CONFLICT, "工具配置已变更，请刷新后重试");
            }
        } catch (DuplicateKeyException exception) {
            throw duplicate(exception);
        }
        target.setCreatedAt(existing.getCreatedAt());
        target.setCreatedBy(existing.getCreatedBy());
        return converter.toDetail(target);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String publicId) {
        ToolDefinitionDO existing = requireExisting(publicId);
        if (!ToolSourceType.HTTP.code().equals(existing.getSourceType())) {
            throw new BizException(ErrorCode.CONFLICT, "内置与 MCP 来源的工具由同步机制维护，不能手工删除");
        }
        if (tools.deleteById(existing.getId()) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "工具不存在");
        }
    }

    @Override
    public ToolSyncResp sync() {
        return syncService.syncAll();
    }

    // ── 内部方法 ────────────────────────────────────────────────────

    /** 构造 http 来源的 config：url / method / headers / timeoutMs。 */
    private Map<String, Object> buildHttpConfig(String url, String method, Integer timeoutMs,
                                                 Map<String, Object> storedHeaders, Map<String, String> submittedHeaders) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put(ToolConfig.URL, ToolSupport.url(url));
        config.put(ToolConfig.METHOD, ToolSupport.method(method));
        config.put(ToolConfig.HEADERS, mergeHeaders(storedHeaders, submittedHeaders));
        config.put(ToolConfig.TIMEOUT_MS, ToolSupport.timeoutMs(timeoutMs));
        return config;
    }

    /**
     * 合并请求头。
     *
     * <p>{@code submittedHeaders} 为 null 表示「本次没动请求头」，原样保留已落库的值；
     * 传了 map（哪怕是空的）就是完整替换。
     */
    private Map<String, Object> mergeHeaders(Map<String, Object> storedHeaders, Map<String, String> submittedHeaders) {
        if (submittedHeaders == null) {
            return storedHeaders == null ? Map.of() : storedHeaders;
        }
        return headerCipher.mergeHeaders(storedHeaders, ToolSupport.headers(submittedHeaders));
    }

    private ToolDefinitionDO requireExisting(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            throw ToolSupport.invalid("工具标识不能为空");
        }
        var entity = tools.selectOne(new QueryWrapper<ToolDefinitionDO>()
                .eq("public_id", publicId).last("FOR UPDATE"));
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "工具不存在");
        }
        return entity;
    }

    /** 工具标识唯一键覆盖软删除行，删掉的标识不能复用。 */
    private void requireAvailableToolKey(String sourceType, String toolKey, Long currentId) {
        Long ownerId = tools.findIdByToolKey(sourceType, toolKey);
        if (ownerId != null && !ownerId.equals(currentId)) {
            throw new BizException(ErrorCode.CONFLICT, "工具标识已被占用（包括已删除记录）");
        }
    }

    /** 工具名在所有未删除的工具里唯一：内置与 MCP 的名字也算占用，否则 function calling 会撞名。 */
    private void requireAvailableName(String name, Long currentId) {
        Long ownerId = tools.findIdByName(name);
        if (ownerId != null && !ownerId.equals(currentId)) {
            throw new BizException(ErrorCode.CONFLICT, "工具名已被占用");
        }
    }

    private static int size(ToolListQuery query) {
        if (query.getSize() == null) {
            return com.hify.shared.web.PageQuery.DEFAULT_SIZE;
        }
        return Math.max(1, Math.min(query.getSize(), com.hify.shared.web.PageQuery.MAX_SIZE_ADMIN));
    }

    private static String encodeCursor(ToolDefinitionDO row) {
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

    private static String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Integer integer(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private static BizException duplicate(DuplicateKeyException cause) {
        return new BizException(ErrorCode.CONFLICT, "工具唯一标识或名称已被占用", cause);
    }

    private record Cursor(LocalDateTime createdAt, long id) {
    }
}
