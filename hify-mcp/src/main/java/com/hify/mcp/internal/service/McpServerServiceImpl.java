package com.hify.mcp.internal.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.hify.mcp.api.McpConnectionTestResp;
import com.hify.mcp.api.McpServerCreateReq;
import com.hify.mcp.api.McpServerListQuery;
import com.hify.mcp.api.McpServerListResp;
import com.hify.mcp.api.McpServerService;
import com.hify.mcp.api.McpServerSummaryResp;
import com.hify.mcp.api.McpServerUpdateReq;
import com.hify.mcp.api.McpToolSyncResp;
import com.hify.mcp.internal.client.McpClientManager;
import com.hify.mcp.internal.converter.McpConverter;
import com.hify.mcp.internal.entity.McpServerDO;
import com.hify.mcp.internal.mapper.McpServerMapper;
import com.hify.shared.crypto.SensitiveHeaderCipher;
import com.hify.shared.exception.BizException;
import com.hify.shared.exception.ErrorCode;
import com.hify.shared.id.IdGenerator;
import com.hify.shared.mybatis.CursorCodec;
import com.hify.shared.web.PageQuery;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * MCP 服务的增删改查、连接测试与工具同步。
 *
 * <p>事务只包数据库操作，不发起 MCP 调用（CLAUDE.md §3.1 E5）——
 * 连上游是慢能力，不能占着数据库连接。
 */
@Service
public class McpServerServiceImpl implements McpServerService {

    /** 连接测试结果里最多回多少个工具名。 */
    private static final int MAX_REPORTED_TOOL_NAMES = 50;

    private final McpServerMapper servers;
    private final McpClientManager clients;
    private final McpToolDiscovery discovery;
    private final SensitiveHeaderCipher headerCipher;
    private final McpConverter converter;

    public McpServerServiceImpl(McpServerMapper servers, McpClientManager clients,
                                McpToolDiscovery discovery, SensitiveHeaderCipher headerCipher,
                                McpConverter converter) {
        this.servers = servers;
        this.clients = clients;
        this.discovery = discovery;
        this.headerCipher = headerCipher;
        this.converter = converter;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public McpServerSummaryResp create(McpServerCreateReq request) {
        if (request == null) {
            throw McpSupport.invalid("服务配置不能为空");
        }
        String name = McpSupport.name(request.getName());
        requireAvailableName(name, null);
        McpSupport.transport(request.getTransport(), request.getCommand(), request.getUrl());

        McpServerDO entity = new McpServerDO();
        entity.setPublicId(IdGenerator.nextUlid());
        entity.setName(name);
        entity.setTransport(request.getTransport());
        entity.setCommand(McpSupport.command(request.getCommand()));
        entity.setArgs(McpSupport.args(request.getArgs()));
        entity.setEnv(McpSupport.env(request.getEnv()));
        entity.setUrl(McpSupport.url(request.getUrl()));
        entity.setHeaders(headerCipher.encryptHeaders(McpSupport.headers(request.getHeaders())));
        entity.setEnabled(request.getEnabled() == null || request.getEnabled());
        entity.setStatus(McpSupport.STATUS_UNKNOWN);
        entity.setConfigVersion(McpSupport.INITIAL_VERSION);
        try {
            servers.insert(entity);
        } catch (DuplicateKeyException exception) {
            throw new BizException(ErrorCode.CONFLICT, "MCP 服务名称已被占用", exception);
        }
        return converter.toSummary(entity);
    }

    @Override
    public McpServerListResp list(McpServerListQuery query) {
        McpServerListQuery effective = query == null ? new McpServerListQuery() : query;
        var wrapper = new QueryWrapper<McpServerDO>();
        if (effective.getTransport() != null && !effective.getTransport().isBlank()) {
            wrapper.eq("transport", effective.getTransport());
        }
        if (effective.getStatus() != null && !effective.getStatus().isBlank()) {
            wrapper.eq("status", effective.getStatus());
        }
        if (effective.getEnabled() != null) {
            wrapper.eq("enabled", effective.getEnabled());
        }
        String keyword = effective.getKeyword() == null ? null : effective.getKeyword().trim();
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like("name", keyword);
        }
        if (effective.getCursor() != null && !effective.getCursor().isBlank()) {
            var cursor = CursorCodec.decode(effective.getCursor());
            wrapper.apply("(created_at, id) < ({0}, {1})", cursor.createdAt(), cursor.id());
        }
        int size = size(effective);
        wrapper.orderByDesc("created_at", "id").last("LIMIT " + (size + 1));

        List<McpServerDO> rows = servers.selectList(wrapper);
        boolean hasNext = rows.size() > size;
        List<McpServerDO> visible = rows.subList(0, Math.min(rows.size(), size));
        List<McpServerSummaryResp> items = new ArrayList<>();
        visible.forEach(row -> items.add(converter.toSummary(row)));

        McpServerListResp result = new McpServerListResp();
        result.setItems(items);
        result.setHasNext(hasNext);
        if (hasNext) {
            var last = visible.getLast();
            result.setNextCursor(CursorCodec.encode(last.getCreatedAt(), last.getId()));
        }
        return result;
    }

    @Override
    public McpServerSummaryResp get(String publicId) {
        return converter.toSummary(requireExisting(publicId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public McpServerSummaryResp update(String publicId, McpServerUpdateReq request) {
        if (request == null) {
            throw McpSupport.invalid("服务配置不能为空");
        }
        McpServerDO existing = requireExisting(publicId);
        if (request.getConfigVersion() == null
                || !Objects.equals(existing.getConfigVersion(), request.getConfigVersion())) {
            throw new BizException(ErrorCode.CONFLICT, "MCP 服务配置已变更，请刷新后重试");
        }
        String transport = request.getTransport() == null ? existing.getTransport() : request.getTransport();
        String command = request.getCommand() == null ? existing.getCommand() : request.getCommand();
        String url = request.getUrl() == null ? existing.getUrl() : request.getUrl();
        McpSupport.transport(transport, command, url);

        String name = request.getName() == null ? existing.getName() : McpSupport.name(request.getName());
        requireAvailableName(name, existing.getId());

        McpServerDO target = new McpServerDO();
        target.setId(existing.getId());
        target.setPublicId(existing.getPublicId());
        target.setName(name);
        target.setTransport(transport);
        target.setCommand(McpSupport.command(command));
        target.setArgs(request.getArgs() == null ? existing.getArgs() : McpSupport.args(request.getArgs()));
        target.setEnv(request.getEnv() == null ? existing.getEnv() : McpSupport.env(request.getEnv()));
        target.setUrl(McpSupport.url(url));
        target.setHeaders(request.getHeaders() == null
                ? existing.getHeaders()
                : headerCipher.mergeHeaders(existing.getHeaders(), request.getHeaders()));
        target.setEnabled(request.getEnabled() == null ? existing.getEnabled() : request.getEnabled());
        target.setStatus(existing.getStatus());
        target.setConfigVersion(existing.getConfigVersion() + 1);
        try {
            int changed = servers.update(target, new UpdateWrapper<McpServerDO>()
                    .eq("id", existing.getId()).eq("config_version", request.getConfigVersion()));
            if (changed == 0) {
                throw new BizException(ErrorCode.CONFLICT, "MCP 服务配置已变更，请刷新后重试");
            }
        } catch (DuplicateKeyException exception) {
            throw new BizException(ErrorCode.CONFLICT, "MCP 服务名称已被占用", exception);
        }
        // 配置变了，缓存的客户端连的是老地址
        clients.evict(existing.getPublicId());
        target.setCreatedAt(existing.getCreatedAt());
        target.setToolCache(existing.getToolCache());
        target.setToolCacheAt(existing.getToolCacheAt());
        target.setLastCheckedAt(existing.getLastCheckedAt());
        return converter.toSummary(target);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String publicId) {
        McpServerDO existing = requireExisting(publicId);
        if (servers.deleteById(existing.getId()) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "MCP 服务不存在");
        }
        clients.evict(existing.getPublicId());
    }

    @Override
    public McpConnectionTestResp test(String publicId) {
        McpServerDO server = requireExisting(publicId);
        var result = discovery.discover(server);
        if (!result.connected()) {
            return new McpConnectionTestResp(false, null, null, null, 0, List.of(), result.message());
        }
        List<String> names = result.tools().stream()
                .limit(MAX_REPORTED_TOOL_NAMES)
                .map(McpSchema.Tool::name)
                .toList();
        return new McpConnectionTestResp(true, result.serverName(), null,
                result.protocolVersion(), result.tools().size(), names, null);
    }

    @Override
    public McpToolSyncResp syncTools() {
        List<McpToolSyncResp.ServerStat> stats = new ArrayList<>();
        int discovered = 0;
        int failed = 0;
        for (var result : discovery.discoverAll()) {
            if (result.connected()) {
                discovered += result.tools().size();
                stats.add(new McpToolSyncResp.ServerStat(result.serverPublicId(), result.serverName(),
                        true, result.tools().size(), null));
            } else {
                failed++;
                stats.add(new McpToolSyncResp.ServerStat(result.serverPublicId(), result.serverName(),
                        false, 0, result.message()));
            }
        }
        return new McpToolSyncResp(stats, discovered, failed);
    }

    // ── 内部方法 ────────────────────────────────────────────────────




    private McpServerDO requireExisting(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            throw McpSupport.invalid("服务标识不能为空");
        }
        McpServerDO entity = servers.selectOne(new QueryWrapper<McpServerDO>().eq("public_id", publicId));
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "MCP 服务不存在");
        }
        return entity;
    }

    /** 唯一键覆盖软删行：删掉的服务名称不能复用。 */
    private void requireAvailableName(String name, Long currentId) {
        Long ownerId = servers.findIdByName(name);
        if (ownerId != null && !ownerId.equals(currentId)) {
            throw new BizException(ErrorCode.CONFLICT, "MCP 服务名称已被占用（包括已删除记录）");
        }
    }


    private static int size(McpServerListQuery query) {
        if (query.getSize() == null) {
            return PageQuery.DEFAULT_SIZE;
        }
        return Math.max(1, Math.min(query.getSize(), PageQuery.MAX_SIZE_ADMIN));
    }

}
