package com.hify.knowledge.internal.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.hify.ai.api.ModelListQuery;
import com.hify.ai.api.ModelService;
import com.hify.ai.api.ModelSummaryResp;
import com.hify.knowledge.api.EmbeddingModelOption;
import com.hify.knowledge.api.KnowledgeBaseCreateReq;
import com.hify.knowledge.api.KnowledgeBaseListQuery;
import com.hify.knowledge.api.KnowledgeBaseListResp;
import com.hify.knowledge.api.KnowledgeBaseService;
import com.hify.knowledge.api.KnowledgeBaseSummaryResp;
import com.hify.knowledge.api.KnowledgeBaseUpdateReq;
import com.hify.knowledge.internal.converter.KnowledgeConverter;
import com.hify.knowledge.internal.entity.KnowledgeBaseDO;
import com.hify.knowledge.internal.mapper.DocumentMapper;
import com.hify.knowledge.internal.mapper.KnowledgeBaseMapper;
import com.hify.shared.exception.BizException;
import com.hify.shared.exception.ErrorCode;
import com.hify.shared.id.IdGenerator;
import com.hify.shared.mybatis.CursorCodec;
import com.hify.shared.web.PageQuery;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 知识库的增删改查。
 *
 * <p>事务只包数据库操作，不发起模型调用（CLAUDE.md §3.1 E5）。
 */
@Service
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {

    /** 初始配置版本。 */
    private static final long INITIAL_VERSION = 1L;

    private final KnowledgeBaseMapper knowledgeBases;
    private final DocumentMapper documents;
    private final ModelService modelService;
    private final KnowledgeConverter converter;

    public KnowledgeBaseServiceImpl(KnowledgeBaseMapper knowledgeBases, DocumentMapper documents,
                                    ModelService modelService, KnowledgeConverter converter) {
        this.knowledgeBases = knowledgeBases;
        this.documents = documents;
        this.modelService = modelService;
        this.converter = converter;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeBaseSummaryResp create(KnowledgeBaseCreateReq request) {
        if (request == null) {
            throw KnowledgeSupport.invalid("知识库配置不能为空");
        }
        String name = KnowledgeSupport.name(request.getName());
        requireAvailableName(name, null);
        int[] chunk = KnowledgeSupport.chunkParams(request.getChunkSize(), request.getChunkOverlap());
        ModelSummaryResp model = requireEmbeddingModel(request.getEmbeddingModel());

        KnowledgeBaseDO entity = new KnowledgeBaseDO();
        entity.setPublicId(IdGenerator.nextUlid());
        entity.setName(name);
        entity.setDescription(request.getDescription() == null ? "" : request.getDescription());
        entity.setEmbeddingModel(model.getPublicId());
        entity.setEmbeddingDim(model.getEmbeddingDimension());
        entity.setChunkSize(chunk[0]);
        entity.setChunkOverlap(chunk[1]);
        entity.setDocCount(0);
        entity.setStatus(KnowledgeSupport.kbStatus(request.getStatus()));
        entity.setConfigVersion(INITIAL_VERSION);
        try {
            knowledgeBases.insert(entity);
        } catch (DuplicateKeyException exception) {
            throw new BizException(ErrorCode.CONFLICT, "知识库名称已被占用", exception);
        }
        return converter.toSummary(entity);
    }

    @Override
    public KnowledgeBaseListResp list(KnowledgeBaseListQuery query) {
        KnowledgeBaseListQuery effective = query == null ? new KnowledgeBaseListQuery() : query;
        var wrapper = new QueryWrapper<KnowledgeBaseDO>();
        if (effective.getStatus() != null && !effective.getStatus().isBlank()) {
            wrapper.eq("status", effective.getStatus());
        }
        String keyword = effective.getKeyword() == null ? null : effective.getKeyword().trim();
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(item -> item.like("name", keyword).or().like("description", keyword));
        }
        if (effective.getCursor() != null && !effective.getCursor().isBlank()) {
            var cursor = CursorCodec.decode(effective.getCursor());
            wrapper.apply("(created_at, id) < ({0}, {1})", cursor.createdAt(), cursor.id());
        }
        int size = size(effective);
        wrapper.orderByDesc("created_at", "id").last("LIMIT " + (size + 1));

        List<KnowledgeBaseDO> rows = knowledgeBases.selectList(wrapper);
        boolean hasNext = rows.size() > size;
        List<KnowledgeBaseDO> visible = rows.subList(0, Math.min(rows.size(), size));
        List<KnowledgeBaseSummaryResp> items = new ArrayList<>();
        visible.forEach(row -> items.add(converter.toSummary(row)));

        KnowledgeBaseListResp result = new KnowledgeBaseListResp();
        result.setItems(items);
        result.setHasNext(hasNext);
        if (hasNext) {
            var last = visible.getLast();
            result.setNextCursor(CursorCodec.encode(last.getCreatedAt(), last.getId()));
        }
        return result;
    }

    @Override
    public KnowledgeBaseSummaryResp get(String publicId) {
        return converter.toSummary(requireExisting(publicId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeBaseSummaryResp update(String publicId, KnowledgeBaseUpdateReq request) {
        if (request == null) {
            throw KnowledgeSupport.invalid("知识库配置不能为空");
        }
        KnowledgeBaseDO existing = requireExisting(publicId);
        if (request.getConfigVersion() == null
                || !Objects.equals(existing.getConfigVersion(), request.getConfigVersion())) {
            throw new BizException(ErrorCode.CONFLICT, "知识库配置已变更，请刷新后重试");
        }
        String name = request.getName() == null ? existing.getName() : KnowledgeSupport.name(request.getName());
        requireAvailableName(name, existing.getId());

        KnowledgeBaseDO target = new KnowledgeBaseDO();
        target.setId(existing.getId());
        target.setPublicId(existing.getPublicId());
        target.setName(name);
        target.setDescription(request.getDescription() == null
                ? existing.getDescription() : request.getDescription());
        target.setStatus(request.getStatus() == null
                ? existing.getStatus() : KnowledgeSupport.kbStatus(request.getStatus()));
        target.setConfigVersion(existing.getConfigVersion() + 1);
        try {
            int changed = knowledgeBases.update(target, new UpdateWrapper<KnowledgeBaseDO>()
                    .eq("id", existing.getId()).eq("config_version", request.getConfigVersion()));
            if (changed == 0) {
                throw new BizException(ErrorCode.CONFLICT, "知识库配置已变更，请刷新后重试");
            }
        } catch (DuplicateKeyException exception) {
            throw new BizException(ErrorCode.CONFLICT, "知识库名称已被占用", exception);
        }
        target.setEmbeddingModel(existing.getEmbeddingModel());
        target.setEmbeddingDim(existing.getEmbeddingDim());
        target.setChunkSize(existing.getChunkSize());
        target.setChunkOverlap(existing.getChunkOverlap());
        target.setDocCount(existing.getDocCount());
        target.setCreatedAt(existing.getCreatedAt());
        return converter.toSummary(target);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String publicId) {
        KnowledgeBaseDO existing = requireExisting(publicId);
        if (documents.countByKbId(existing.getId()) > 0) {
            throw new BizException(ErrorCode.CONFLICT, "请先删除该知识库下的文档");
        }
        if (knowledgeBases.deleteById(existing.getId()) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "知识库不存在");
        }
    }

    @Override
    public List<EmbeddingModelOption> listEmbeddingModels() {
        ModelListQuery query = new ModelListQuery();
        query.setModelType("embedding");
        query.setEnabled(true);
        query.setSize(200);
        List<EmbeddingModelOption> options = new ArrayList<>();
        for (ModelSummaryResp model : modelService.list(query).getItems()) {
            options.add(converter.toModelOption(model));
        }
        return options;
    }

    // ── 内部方法 ────────────────────────────────────────────────────

    /**
     * 校验所选模型能用于向量化。
     *
     * <p>维度必须是 {@code vec_chunk} 的 1536 —— 维度对不上，建出来的库必然写不进向量。
     */
    private ModelSummaryResp requireEmbeddingModel(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            throw KnowledgeSupport.invalid("请选择向量化模型");
        }
        ModelSummaryResp model = modelService.get(publicId);
        if (!"embedding".equals(model.getModelType())) {
            throw KnowledgeSupport.invalid("所选模型不是 embedding 模型");
        }
        if (!Boolean.TRUE.equals(model.getEnabled())) {
            throw KnowledgeSupport.invalid("所选模型已停用");
        }
        Integer dimension = model.getEmbeddingDimension();
        if (dimension == null) {
            throw KnowledgeSupport.invalid("所选模型还没确认向量维度，请先在模型管理里填写");
        }
        if (dimension != KnowledgeSupport.EMBEDDING_DIMENSION) {
            throw KnowledgeSupport.invalid("所选模型的向量维度是 " + dimension
                    + "，与向量库的 " + KnowledgeSupport.EMBEDDING_DIMENSION + " 不一致，换一个模型");
        }
        return model;
    }

    private KnowledgeBaseDO requireExisting(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            throw KnowledgeSupport.invalid("知识库标识不能为空");
        }
        KnowledgeBaseDO entity = knowledgeBases.selectOne(new QueryWrapper<KnowledgeBaseDO>()
                .eq("public_id", publicId));
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "知识库不存在");
        }
        return entity;
    }

    /** 唯一键覆盖软删行：删掉的知识库名称不能复用。 */
    private void requireAvailableName(String name, Long currentId) {
        Long ownerId = knowledgeBases.findIdByName(name);
        if (ownerId != null && !ownerId.equals(currentId)) {
            throw new BizException(ErrorCode.CONFLICT, "知识库名称已被占用（包括已删除记录）");
        }
    }

    private static int size(KnowledgeBaseListQuery query) {
        if (query.getSize() == null) {
            return PageQuery.DEFAULT_SIZE;
        }
        return Math.max(1, Math.min(query.getSize(), PageQuery.MAX_SIZE_ADMIN));
    }
}
