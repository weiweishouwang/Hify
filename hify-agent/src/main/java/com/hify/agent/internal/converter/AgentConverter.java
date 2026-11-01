package com.hify.agent.internal.converter;

import com.hify.agent.api.AgentDetailResp;
import com.hify.agent.api.AgentSummaryResp;
import com.hify.agent.internal.entity.AgentDO;
import com.hify.agent.internal.service.AgentSupport;
import com.hify.ai.api.ModelSummaryResp;
import org.springframework.stereotype.Component;

import java.util.List;

/** Agent 数据对象与对外 DTO 的转换。转换逻辑不堆在 service 里。 */
@Component
public class AgentConverter {

    /**
     * 摘要。
     *
     * @param row             数据对象
     * @param model           主模型；解析不到时是 null
     * @param unavailableCount 当前不可用的引用数（工具 + 知识库）
     * @return 摘要
     */
    public AgentSummaryResp toSummary(AgentDO row, ModelSummaryResp model, int unavailableCount) {
        AgentSummaryResp resp = new AgentSummaryResp();
        resp.setPublicId(row.getPublicId());
        resp.setName(row.getName());
        resp.setDescription(row.getDescription());
        resp.setModelPublicId(model == null ? null : model.getPublicId());
        resp.setModelDisplayName(model == null ? null : model.getDisplayName());
        resp.setStatus(row.getStatus());
        resp.setToolCount(row.getToolIds() == null ? 0 : row.getToolIds().size());
        resp.setKnowledgeCount(row.getKnowledgeIds() == null ? 0 : row.getKnowledgeIds().size());
        resp.setUnavailableCount(unavailableCount);
        resp.setConfigVersion(row.getConfigVersion());
        resp.setCreatedAt(row.getCreatedAt());
        resp.setUpdatedAt(row.getUpdatedAt());
        return resp;
    }

    /**
     * 详情。
     *
     * @param row             数据对象
     * @param model           主模型；解析不到时是 null
     * @param unavailableCount 当前不可用的引用数（工具 + 知识库）
     * @return 详情
     */
    public AgentDetailResp toDetail(AgentDO row, ModelSummaryResp model, int unavailableCount) {
        AgentDetailResp resp = new AgentDetailResp();
        resp.setId(row.getId());
        resp.setPublicId(row.getPublicId());
        resp.setName(row.getName());
        resp.setDescription(row.getDescription());
        resp.setModelPublicId(model == null ? null : model.getPublicId());
        resp.setModelDisplayName(model == null ? null : model.getDisplayName());
        resp.setPromptTemplate(row.getPromptTemplate());
        resp.setToolIds(row.getToolIds() == null ? List.of() : List.copyOf(row.getToolIds()));
        resp.setKnowledgeIds(row.getKnowledgeIds() == null ? List.of() : List.copyOf(row.getKnowledgeIds()));
        resp.setParamConfig(AgentSupport.toParamConfig(row.getParamConfig()));
        resp.setStatus(row.getStatus());
        resp.setAppType(row.getAppType());
        resp.setUnavailableCount(unavailableCount);
        resp.setConfigVersion(row.getConfigVersion());
        resp.setCreatedAt(row.getCreatedAt());
        resp.setUpdatedAt(row.getUpdatedAt());
        return resp;
    }
}
