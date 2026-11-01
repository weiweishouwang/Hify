package com.hify.agent.internal.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hify.agent.api.AgentBundle;
import com.hify.agent.api.AgentCatalog;
import com.hify.agent.api.AgentStatus;
import com.hify.agent.internal.entity.AgentDO;
import com.hify.agent.internal.mapper.AgentMapper;
import com.hify.ai.api.ModelService;
import com.hify.ai.api.ModelSummaryResp;
import com.hify.knowledge.api.KnowledgeBaseService;
import com.hify.knowledge.api.KnowledgeBaseSummaryResp;
import com.hify.shared.exception.BizException;
import com.hify.shared.exception.ErrorCode;
import com.hify.tool.api.ToolBinding;
import com.hify.tool.api.ToolDefinitionService;
import com.hify.tool.api.ToolSpec;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 把一份 Agent 配置组装成可运行的 Agent。
 *
 * <p><b>只组装，不执行</b> —— 不调模型、不调工具、不检索知识库
 * （CLAUDE.md §2.1 的不变量）。组装只查本地配置表与工具目录表，
 * 不发起任何上游调用。
 *
 * <p><b>绑定的工具或知识库后来被停用时，组装结果里就没有它们</b> ——
 * 停用的东西不该被交给模型。这也是为什么管理台的列表要显示「有 N 项引用不可用」。
 */
@Service
public class AgentAssembler implements AgentCatalog {

    private final AgentMapper agents;
    private final ModelService models;
    private final ToolDefinitionService tools;
    private final KnowledgeBaseService knowledgeBases;

    public AgentAssembler(AgentMapper agents, ModelService models, ToolDefinitionService tools,
                          KnowledgeBaseService knowledgeBases) {
        this.agents = agents;
        this.models = models;
        this.tools = tools;
        this.knowledgeBases = knowledgeBases;
    }

    @Override
    public AgentBundle assemble(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            throw AgentSupport.invalid("Agent 标识不能为空");
        }
        AgentDO row = agents.selectOne(new QueryWrapper<AgentDO>().eq("public_id", publicId));
        if (row == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Agent 不存在");
        }
        if (!AgentStatus.PUBLISHED.code().equals(row.getStatus())) {
            throw new BizException(ErrorCode.CONFLICT, "Agent 未发布或已停用，不能用于对话");
        }

        ModelSummaryResp model = resolveModel(row);
        if (model == null) {
            throw new BizException(ErrorCode.CONFLICT, "Agent 绑定的主模型已不存在");
        }
        if (!Boolean.TRUE.equals(model.getEnabled())) {
            throw new BizException(ErrorCode.CONFLICT, "Agent 绑定的主模型已停用");
        }

        return new AgentBundle(
                row.getPublicId(),
                row.getName(),
                row.getStatus(),
                row.getPromptTemplate(),
                model,
                resolveTools(row),
                resolveKnowledgeBases(row),
                AgentSupport.toParamConfig(row.getParamConfig()));
    }

    private ModelSummaryResp resolveModel(AgentDO row) {
        if (row.getModelId() == null) {
            return null;
        }
        List<ModelSummaryResp> found = models.listByIds(List.of(row.getModelId()));
        return found.isEmpty() ? null : found.getFirst();
    }

    /** 只留启用的工具。 */
    private List<ToolSpec> resolveTools(AgentDO row) {
        List<String> ids = row.getToolIds();
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<ToolSpec> specs = new ArrayList<>();
        for (ToolBinding binding : tools.listBindingsByPublicIds(ids)) {
            if (binding.enabled()) {
                specs.add(binding.spec());
            }
        }
        return specs;
    }

    /** 只留启用（active）的知识库。 */
    private List<KnowledgeBaseSummaryResp> resolveKnowledgeBases(AgentDO row) {
        List<String> ids = row.getKnowledgeIds();
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<KnowledgeBaseSummaryResp> result = new ArrayList<>();
        for (KnowledgeBaseSummaryResp kb : knowledgeBases.listByPublicIds(ids)) {
            if ("active".equals(kb.getStatus())) {
                result.add(kb);
            }
        }
        return result;
    }
}
