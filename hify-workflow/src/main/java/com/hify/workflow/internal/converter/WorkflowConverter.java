package com.hify.workflow.internal.converter;

import com.hify.shared.json.JsonUtils;
import com.hify.workflow.api.WorkflowDetailResp;
import com.hify.workflow.api.WorkflowDefinition;
import com.hify.workflow.api.WorkflowSummaryResp;
import com.hify.workflow.internal.entity.WorkflowDefinitionDO;
import org.springframework.stereotype.Component;

/** 工作流数据对象与对外 DTO 的转换。转换逻辑不堆在 service 里。 */
@Component
public class WorkflowConverter {

    private final JsonUtils json;

    public WorkflowConverter(JsonUtils json) {
        this.json = json;
    }

    /** 摘要。 */
    public WorkflowSummaryResp toSummary(WorkflowDefinitionDO row) {
        WorkflowSummaryResp resp = new WorkflowSummaryResp();
        resp.setPublicId(row.getPublicId());
        resp.setName(row.getName());
        resp.setDescription(row.getDescription());
        resp.setVersion(row.getVersion());
        resp.setStatus(row.getStatus());
        resp.setNodeCount(row.getDefinition() == null || row.getDefinition().getNodes() == null
                ? 0 : row.getDefinition().getNodes().size());
        resp.setCreatedAt(row.getCreatedAt());
        resp.setUpdatedAt(row.getUpdatedAt());
        return resp;
    }

    /** 详情：带上定义原文（格式化成可读的 JSON 文本）。 */
    public WorkflowDetailResp toDetail(WorkflowDefinitionDO row) {
        WorkflowDetailResp resp = new WorkflowDetailResp();
        WorkflowSummaryResp summary = toSummary(row);
        resp.setPublicId(summary.getPublicId());
        resp.setName(summary.getName());
        resp.setDescription(summary.getDescription());
        resp.setVersion(summary.getVersion());
        resp.setStatus(summary.getStatus());
        resp.setNodeCount(summary.getNodeCount());
        resp.setCreatedAt(summary.getCreatedAt());
        resp.setUpdatedAt(summary.getUpdatedAt());
        resp.setDefinition(pretty(row.getDefinition()));
        return resp;
    }

    /** 定义 → 可读 JSON 文本。 */
    public String pretty(WorkflowDefinition definition) {
        if (definition == null) {
            return "{}";
        }
        try {
            return json.mapper().writerWithDefaultPrettyPrinter().writeValueAsString(definition);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            return "{}";
        }
    }
}
