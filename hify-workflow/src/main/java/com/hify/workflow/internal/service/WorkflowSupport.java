package com.hify.workflow.internal.service;

import com.hify.shared.exception.BizException;
import com.hify.shared.exception.ErrorCode;
import com.hify.shared.json.JsonUtils;
import org.springframework.stereotype.Component;
import com.hify.workflow.api.WorkflowDefinition;
import com.hify.workflow.api.WorkflowEdge;
import com.hify.workflow.api.WorkflowNode;
import com.hify.workflow.api.WorkflowNodeType;
import com.hify.workflow.api.WorkflowOperator;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 工作流定义的解析与校验。
 *
 * <p><b>保存时就校验</b>：定义是用户手写的 JSON，跑起来才发现少个字段，
 * 排查成本高得多（CLAUDE.md §3.1 的精神：把错误挡在离源头最近的地方）。
 */
@Component
public class WorkflowSupport {

    /** 状态：草稿。 */
    public static final String STATUS_DRAFT = "draft";

    /** 状态：已发布。 */
    public static final String STATUS_PUBLISHED = "published";

    /** 状态：已停用。 */
    public static final String STATUS_DISABLED = "disabled";

    /** 运行状态：进行中。 */
    public static final String RUN_RUNNING = "running";

    /** 运行状态：成功。 */
    public static final String RUN_SUCCEEDED = "succeeded";

    /** 运行状态：失败。 */
    public static final String RUN_FAILED = "failed";

    /** 初始版本号。 */
    public static final int INITIAL_VERSION = 1;

    /** 一次运行最多执行多少个节点 —— 定义里有环时兜底。 */
    public static final int MAX_STEPS = 100;

    private final JsonUtils json;

    public WorkflowSupport(JsonUtils json) {
        this.json = json;
    }

    /** 参数错误。 */
    public static BizException invalid(String message) {
        return new BizException(ErrorCode.PARAM_ERROR, message);
    }

    /** 名称。 */
    public static String name(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            throw invalid("名称不能为空");
        }
        if (trimmed.length() > 128) {
            throw invalid("名称最长 128 字符");
        }
        return trimmed;
    }

    /** 描述。 */
    public static String description(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.length() > 512) {
            throw invalid("描述最长 512 字符");
        }
        return trimmed;
    }

    /** 状态：draft / published / disabled。 */
    public static String status(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String status = value.trim();
        if (!STATUS_DRAFT.equals(status) && !STATUS_PUBLISHED.equals(status) && !STATUS_DISABLED.equals(status)) {
            throw invalid("工作流状态只支持 draft、published 或 disabled");
        }
        return status;
    }

    /**
     * 解析定义 JSON 并做结构校验。
     *
     * @param text 定义原文
     * @return 解析后的定义
     */
    public WorkflowDefinition parse(String text) {
        if (text == null || text.isBlank()) {
            throw invalid("工作流定义不能为空");
        }
        WorkflowDefinition definition;
        try {
            definition = json.fromJson(text, WorkflowDefinition.class);
        } catch (RuntimeException exception) {
            throw invalid("工作流定义不是合法 JSON: " + exception.getMessage());
        }
        validate(definition);
        return definition;
    }

    /** 校验定义的结构与图。 */
    public static void validate(WorkflowDefinition definition) {
        if (definition == null) {
            throw invalid("工作流定义不能为空");
        }
        List<WorkflowNode> nodes = definition.getNodes();
        if (nodes == null || nodes.isEmpty()) {
            throw invalid("工作流至少要有一个节点");
        }
        Map<String, WorkflowNode> byId = new LinkedHashMap<>();
        Set<String> outputNames = new LinkedHashSet<>();
        boolean hasOutputNode = false;
        for (WorkflowNode node : nodes) {
            if (node == null) {
                throw invalid("工作流里有空节点");
            }
            String id = node.getId() == null ? "" : node.getId().trim();
            if (id.isEmpty()) {
                throw invalid("节点必须有 id");
            }
            if (byId.containsKey(id)) {
                throw invalid("节点 id 重复: " + id);
            }
            byId.put(id, node);
            WorkflowNodeType type = WorkflowNodeType.fromCode(node.getType());
            validateNode(node, type, definition, outputNames);
            if (type == WorkflowNodeType.OUTPUT) {
                hasOutputNode = true;
            }
        }
        if (!hasOutputNode) {
            throw invalid("工作流至少要有一个 output 节点");
        }

        List<WorkflowEdge> edges = definition.getEdges();
        Map<String, List<String>> outgoing = new LinkedHashMap<>();
        Map<String, Integer> inDegree = new HashMap<>();
        for (String id : byId.keySet()) {
            inDegree.put(id, 0);
        }
        if (edges != null) {
            for (WorkflowEdge edge : edges) {
                if (edge == null || edge.from() == null || edge.to() == null) {
                    throw invalid("连线不能为空");
                }
                String from = edge.from().trim();
                String to = edge.to().trim();
                if (!byId.containsKey(from)) {
                    throw invalid("连线的起点节点不存在: " + from);
                }
                if (!byId.containsKey(to)) {
                    throw invalid("连线的终点节点不存在: " + to);
                }
                if (WorkflowNodeType.IF == WorkflowNodeType.fromCode(byId.get(from).getType())) {
                    throw invalid("条件分支节点的走向由 then / otherwise 指定，不要写进 edges: " + from);
                }
                outgoing.computeIfAbsent(from, key -> new ArrayList<>()).add(to);
                inDegree.merge(to, 1, Integer::sum);
            }
        }
        // 条件分支的走向也算连线 —— 图的连通性与环检查都要算上
        for (WorkflowNode node : nodes) {
            if (WorkflowNodeType.IF != WorkflowNodeType.fromCode(node.getType())) {
                continue;
            }
            for (String target : List.of(node.getThen(), node.getOtherwise())) {
                String id = target == null ? "" : target.trim();
                if (id.isEmpty()) {
                    throw invalid("条件分支节点必须指定 then 与 otherwise: " + node.getId());
                }
                if (!byId.containsKey(id)) {
                    throw invalid("条件分支指向的节点不存在: " + id);
                }
                outgoing.computeIfAbsent(node.getId(), key -> new ArrayList<>()).add(id);
                inDegree.merge(id, 1, Integer::sum);
            }
        }

        long entries = inDegree.values().stream().filter(degree -> degree == 0).count();
        if (entries == 0) {
            throw invalid("工作流没有入口节点");
        }
        if (entries > 1) {
            throw invalid("工作流有多个入口节点，只能有一个起点");
        }
        assertAcyclic(byId, outgoing, inDegree);
    }

    /** 按节点类型检查必填字段。 */
    private static void validateNode(WorkflowNode node, WorkflowNodeType type,
                                     WorkflowDefinition definition, Set<String> outputNames) {
        switch (type) {
            case LLM -> {
                require(node.getPrompt(), "llm 节点必须写 prompt", node);
                if (isBlank(node.getModel()) && isBlank(definition.getModel())) {
                    throw invalid("llm 节点必须指定 model，或在定义顶层指定默认 model: " + node.getId());
                }
                requireOutput(node, outputNames);
            }
            case AGENT -> {
                require(node.getAgent(), "agent 节点必须写 agent", node);
                requireOutput(node, outputNames);
            }
            case TOOL -> {
                require(node.getTool(), "tool 节点必须写 tool", node);
                requireOutput(node, outputNames);
            }
            case KNOWLEDGE -> {
                require(node.getKnowledgeBase(), "knowledge 节点必须写 knowledgeBase", node);
                require(node.getQuery(), "knowledge 节点必须写 query", node);
                requireOutput(node, outputNames);
            }
            case IF -> {
                require(node.getLeft(), "if 节点必须写 left", node);
                WorkflowOperator.fromCode(node.getOperator());
                require(node.getThen(), "if 节点必须写 then", node);
                require(node.getOtherwise(), "if 节点必须写 otherwise", node);
            }
            case OUTPUT -> {
                require(node.getValue(), "output 节点必须写 value", node);
                // 输出节点的名字不查重：条件分支的两条路上各有一个输出节点，
                // 用同一个名字才对（只会有一条路跑起来）
            }
            default -> throw invalid("不支持的节点类型: " + node.getType());
        }
    }

    /** 除条件分支与输出节点外，节点都要有唯一的输出变量名。 */
    private static void requireOutput(WorkflowNode node, Set<String> outputNames) {
        if (isBlank(node.getOutput())) {
            throw invalid("节点必须写 output（输出变量名）: " + node.getId());
        }
        if (!outputNames.add(node.getOutput().trim())) {
            throw invalid("输出变量名重复: " + node.getOutput());
        }
    }

    private static void require(String value, String message, WorkflowNode node) {
        if (isBlank(value)) {
            throw invalid(message + ": " + node.getId());
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** 拓扑排序验环。 */
    private static void assertAcyclic(Map<String, WorkflowNode> byId, Map<String, List<String>> outgoing,
                                      Map<String, Integer> inDegree) {
        Map<String, Integer> degrees = new HashMap<>(inDegree);
        Deque<String> ready = new ArrayDeque<>();
        for (Map.Entry<String, Integer> entry : degrees.entrySet()) {
            if (entry.getValue() == 0) {
                ready.add(entry.getKey());
            }
        }
        int visited = 0;
        while (!ready.isEmpty()) {
            String current = ready.poll();
            visited++;
            for (String next : outgoing.getOrDefault(current, List.of())) {
                int degree = degrees.merge(next, -1, Integer::sum);
                if (degree == 0) {
                    ready.add(next);
                }
            }
        }
        if (visited != byId.size()) {
            throw invalid("工作流里有环，不能执行");
        }
    }

    /** 入口节点 id（入度为 0 的那个）。 */
    public static String entryNodeId(WorkflowDefinition definition) {
        Map<String, Integer> inDegree = new HashMap<>();
        definition.getNodes().forEach(node -> inDegree.put(node.getId(), 0));
        for (WorkflowEdge edge : definition.getEdges() == null ? List.<WorkflowEdge>of() : definition.getEdges()) {
            inDegree.merge(edge.to(), 1, Integer::sum);
        }
        for (WorkflowNode node : definition.getNodes()) {
            if (WorkflowNodeType.IF != WorkflowNodeType.fromCode(node.getType())) {
                continue;
            }
            for (String target : List.of(node.getThen(), node.getOtherwise())) {
                inDegree.merge(target, 1, Integer::sum);
            }
        }
        String entry = null;
        for (Map.Entry<String, Integer> item : inDegree.entrySet()) {
            if (item.getValue() == 0) {
                entry = item.getKey();
            }
        }
        if (entry == null) {
            throw invalid("工作流没有入口节点");
        }
        return entry;
    }

    /**
     * 节点的出边。
     *
     * @param definition 定义
     * @return 节点 id → 终点节点 id 列表
     */
    public static Map<String, List<String>> outgoing(WorkflowDefinition definition) {
        Map<String, List<String>> outgoing = new LinkedHashMap<>();
        if (definition.getEdges() != null) {
            for (WorkflowEdge edge : definition.getEdges()) {
                outgoing.computeIfAbsent(edge.from(), key -> new ArrayList<>()).add(edge.to());
            }
        }
        for (WorkflowNode node : definition.getNodes()) {
            if (WorkflowNodeType.IF != WorkflowNodeType.fromCode(node.getType())) {
                continue;
            }
            outgoing.computeIfAbsent(node.getId(), key -> new ArrayList<>()).add(node.getThen());
            outgoing.computeIfAbsent(node.getId(), key -> new ArrayList<>()).add(node.getOtherwise());
        }
        return outgoing;
    }

    /** 节点的入度。 */
    public static Set<String> targets(WorkflowDefinition definition) {
        Set<String> targets = new HashSet<>();
        Map<String, List<String>> outgoing = outgoing(definition);
        outgoing.values().forEach(targets::addAll);
        return targets;
    }
}
