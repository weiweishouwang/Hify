package com.hify.workflow.internal.service;

import com.hify.agent.api.AgentBundle;
import com.hify.agent.api.AgentCatalog;
import com.hify.ai.api.ChatListener;
import com.hify.ai.api.ChatMessage;
import com.hify.ai.api.ChatRequest;
import com.hify.ai.api.ChatService;
import com.hify.ai.api.ChatToolCall;
import com.hify.ai.api.ChatUsage;
import com.hify.knowledge.api.KnowledgeSearchService;
import com.hify.knowledge.api.SearchQuery;
import com.hify.knowledge.api.SearchResult;
import com.hify.shared.exception.BizException;
import com.hify.shared.exception.ErrorCode;
import com.hify.tool.api.ToolBinding;
import com.hify.tool.api.ToolCatalog;
import com.hify.tool.api.ToolDefinitionService;
import com.hify.tool.api.ToolInvokeContext;
import com.hify.tool.api.ToolResult;
import com.hify.workflow.api.WorkflowDefinition;
import com.hify.workflow.api.WorkflowNode;
import com.hify.workflow.api.WorkflowNodeType;
import com.hify.workflow.api.WorkflowOperator;
import com.hify.workflow.api.WorkflowRunResp;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 工作流执行引擎。
 *
 * <p><b>顺着连线走，每个节点最多执行一次</b>：入口节点是入度为 0 的那个，
 * 执行完沿出边前进；条件分支只走命中的那条边；已经执行过的节点再被走到就跳过
 * —— 这就是汇聚（merge）的语义。
 *
 * <p><b>不跑工具循环</b>：工具循环是 conversation 模块的编排逻辑，
 * 两个编排层平行、互不依赖（CLAUDE.md §2.1）。要调工具用 {@code tool} 节点。
 *
 * <p><b>跑在 llmExecutor 上</b> —— 节点里的模型调用是阻塞的，不能占 Tomcat 线程。
 */
@Service
public class WorkflowEngine {

    /** 输出预览的最大长度；太长会撑爆日志与界面。 */
    private static final int PREVIEW_LENGTH = 200;

    private final ChatService chat;
    private final ToolCatalog tools;
    private final ToolDefinitionService toolDefinitions;
    private final KnowledgeSearchService search;
    private final AgentCatalog agents;
    private final TemplateResolver templates;
    private final WorkflowSupport support;

    public WorkflowEngine(ChatService chat, ToolCatalog tools, ToolDefinitionService toolDefinitions,
                          KnowledgeSearchService search, AgentCatalog agents,
                          TemplateResolver templates, WorkflowSupport support) {
        this.chat = chat;
        this.tools = tools;
        this.toolDefinitions = toolDefinitions;
        this.search = search;
        this.agents = agents;
        this.templates = templates;
        this.support = support;
    }

    /**
     * 执行一次工作流。
     *
     * @param definition 工作流定义
     * @param input      入参
     * @param userId     调用者用户 id；显式携带，不靠 ThreadLocal（会跨线程池串号）
     * @return 运行结果
     */
    public WorkflowRunResp execute(WorkflowDefinition definition, Map<String, Object> input, Long userId) {
        long startNanos = System.nanoTime();
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("input", input == null ? Map.of() : input);
        Map<String, WorkflowNode> nodesById = new LinkedHashMap<>();
        definition.getNodes().forEach(node -> nodesById.put(node.getId(), node));
        Map<String, List<String>> outgoing = WorkflowSupport.outgoing(definition);
        Map<String, ToolBinding> bindings = new HashMap<>();

        Deque<String> queue = new ArrayDeque<>();
        queue.add(WorkflowSupport.entryNodeId(definition));
        Set<String> executed = new HashSet<>();
        Map<String, Object> output = new LinkedHashMap<>();
        List<WorkflowRunResp.WorkflowStep> steps = new ArrayList<>();
        int stepCount = 0;

        try {
            while (!queue.isEmpty()) {
                String nodeId = queue.poll();
                if (!executed.add(nodeId)) {
                    // 已经跑过了 —— 汇聚节点只跑一次
                    continue;
                }
                if (++stepCount > WorkflowSupport.MAX_STEPS) {
                    throw WorkflowSupport.invalid(
                            "执行节点数超过上限 " + WorkflowSupport.MAX_STEPS + "，定义可能有环");
                }
                WorkflowNode node = nodesById.get(nodeId);
                long nodeStart = System.nanoTime();
                Object value = executeNode(node, definition, variables, bindings, userId);
                int nodeLatency = (int) ((System.nanoTime() - nodeStart) / 1_000_000L);

                if (WorkflowNodeType.IF == WorkflowNodeType.fromCode(node.getType())) {
                    boolean taken = Boolean.TRUE.equals(value);
                    steps.add(new WorkflowRunResp.WorkflowStep(nodeId, node.getType(), node.getName(),
                            taken ? "条件成立" : "条件不成立", nodeLatency));
                    queue.add(taken ? node.getThen() : node.getOtherwise());
                    continue;
                }
                if (node.getOutput() != null && !node.getOutput().isBlank()) {
                    variables.put(node.getOutput().trim(), value);
                }
                if (WorkflowNodeType.OUTPUT == WorkflowNodeType.fromCode(node.getType())) {
                    String key = node.getOutput() == null || node.getOutput().isBlank()
                            ? node.getId() : node.getOutput().trim();
                    output.put(key, value);
                }
                steps.add(new WorkflowRunResp.WorkflowStep(nodeId, node.getType(), node.getName(),
                        preview(value), nodeLatency));
                for (String next : outgoing.getOrDefault(nodeId, List.of())) {
                    queue.add(next);
                }
            }
            return new WorkflowRunResp(WorkflowSupport.RUN_SUCCEEDED, output, stepCount,
                    elapsed(startNanos), null, steps);
        } catch (RuntimeException exception) {
            return new WorkflowRunResp(WorkflowSupport.RUN_FAILED, output, stepCount,
                    elapsed(startNanos), messageOf(exception), steps);
        }
    }

    /** 执行一个节点。 */
    private Object executeNode(WorkflowNode node, WorkflowDefinition definition,
                               Map<String, Object> variables, Map<String, ToolBinding> bindings, Long userId) {
        WorkflowNodeType type = WorkflowNodeType.fromCode(node.getType());
        switch (type) {
            case LLM -> {
                String model = node.getModel() == null || node.getModel().isBlank()
                        ? definition.getModel() : node.getModel();
                String prompt = templates.resolve(node.getPrompt(), variables);
                return callModel(model, prompt, node.getParams(), variables, null);
            }
            case AGENT -> {
                AgentBundle bundle = agents.assemble(node.getAgent());
                String system = templates.resolve(bundle.promptTemplate(), variables);
                String userText = templates.resolve(node.getInput(), variables);
                return callModel(bundle.model().getPublicId(), userText, paramsOf(bundle), variables, system);
            }
            case TOOL -> {
                ToolBinding binding = binding(node.getTool(), bindings);
                Map<String, Object> args = resolveArgs(node.getArgs(), variables);
                ToolResult result = tools.invoke(
                        ToolInvokeContext.of(userId, null), binding.spec().sourceType(),
                        binding.spec().toolKey(), args);
                if (!result.success()) {
                    throw new BizException(ErrorCode.UPSTREAM_ERROR,
                            "工具执行失败: " + node.getId() + " - " + result.errorMessage());
                }
                return result.content();
            }
            case KNOWLEDGE -> {
                SearchQuery query = new SearchQuery();
                query.setQuery(templates.resolve(node.getQuery(), variables));
                query.setTopK(node.getTopK() == null ? 3 : Math.max(1, Math.min(node.getTopK(), 20)));
                List<SearchResult> hits = search.search(node.getKnowledgeBase(), query);
                List<String> contents = new ArrayList<>(hits.size());
                for (SearchResult hit : hits) {
                    contents.add(hit.content());
                }
                return contents;
            }
            case IF -> {
                Object left = templates.resolveValue(node.getLeft(), variables);
                Object right = templates.resolveValue(node.getRight(), variables);
                return evaluate(left, node.getOperator(), right);
            }
            case OUTPUT -> {
                return templates.resolveValue(node.getValue(), variables);
            }
            default -> throw WorkflowSupport.invalid("不支持的节点类型: " + node.getType());
        }
    }

    /**
     * 调一次模型，把流式增量攒成完整文本。
     *
     * <p>工作流要的是结果不是过程，所以把增量攒起来；工具调用一律忽略 ——
     * 工作流里的模型调用不挂工具（CLAUDE.md §2.1 的编排层边界）。
     */
    private String callModel(String modelPublicId, String userText, Map<String, Object> params,
                             Map<String, Object> variables, String system) {
        if (modelPublicId == null || modelPublicId.isBlank()) {
            throw WorkflowSupport.invalid("工作流没有指定模型");
        }
        ChatRequest request = new ChatRequest();
        request.setModelPublicId(modelPublicId);
        List<ChatMessage> messages = new ArrayList<>();
        if (system != null && !system.isBlank()) {
            messages.add(ChatMessage.system(system));
        }
        messages.add(ChatMessage.user(userText == null ? "" : userText));
        request.setMessages(messages);
        request.setParams(params == null ? Map.of() : params);

        StringBuilder text = new StringBuilder();
        chat.stream(request, new ChatListener() {
            @Override
            public void onText(String delta) {
                text.append(delta);
            }

            @Override
            public void onToolCall(ChatToolCall call) {
                // 工作流不挂工具，模型要调工具时忽略
            }

            @Override
            public void onUsage(ChatUsage usage) {
                // 工作流不统计 token
            }

            @Override
            public void onFinish(String finishReason) {
                // 结束原因不影响结果
            }
        });
        return text.toString();
    }

    /** 工具目录行；同一轮里缓存下来，别每个节点都查一次。 */
    private ToolBinding binding(String toolPublicId, Map<String, ToolBinding> cache) {
        if (toolPublicId == null || toolPublicId.isBlank()) {
            throw WorkflowSupport.invalid("tool 节点必须写 tool");
        }
        ToolBinding cached = cache.get(toolPublicId);
        if (cached != null) {
            return cached;
        }
        List<ToolBinding> found = toolDefinitions.listBindingsByPublicIds(List.of(toolPublicId));
        if (found.isEmpty()) {
            throw WorkflowSupport.invalid("工具不存在: " + toolPublicId);
        }
        ToolBinding binding = found.getFirst();
        if (!binding.enabled()) {
            throw WorkflowSupport.invalid("工具已停用: " + toolPublicId);
        }
        cache.put(toolPublicId, binding);
        return binding;
    }

    /** 工具入参：值里的 {{变量}} 也要解析，解析不到的非字符串原样保留。 */
    private Map<String, Object> resolveArgs(Map<String, Object> args, Map<String, Object> variables) {
        if (args == null || args.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> resolved = new LinkedHashMap<>();
        args.forEach((key, value) -> resolved.put(key, templates.resolveValue(value, variables)));
        return resolved;
    }

    /** Agent 的参数区转成模型的生成参数。 */
    private Map<String, Object> paramsOf(AgentBundle bundle) {
        var config = bundle.paramConfig();
        Map<String, Object> params = new LinkedHashMap<>();
        if (config == null) {
            return params;
        }
        if (config.getTemperature() != null) {
            params.put("temperature", config.getTemperature());
        }
        if (config.getMaxTokens() != null) {
            params.put("max_tokens", config.getMaxTokens());
        }
        if (config.getTopP() != null) {
            params.put("top_p", config.getTopP());
        }
        return params;
    }

    /** 条件判断。 */
    private boolean evaluate(Object left, String operatorCode, Object right) {
        WorkflowOperator operator = WorkflowOperator.fromCode(operatorCode);
        switch (operator) {
            case IS_EMPTY:
                return isEmpty(left);
            case NOT_EMPTY:
                return !isEmpty(left);
            case EQUALS:
                return text(left).equals(text(right));
            case NOT_EQUALS:
                return !text(left).equals(text(right));
            case CONTAINS:
                return contains(left, text(right));
            case NOT_CONTAINS:
                return !contains(left, text(right));
            case GT:
                return number(left) > number(right);
            case LT:
                return number(left) < number(right);
            default:
                throw WorkflowSupport.invalid("不支持的条件运算符: " + operatorCode);
        }
    }

    private static boolean isEmpty(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof String text) {
            return text.isBlank();
        }
        if (value instanceof List<?> list) {
            return list.isEmpty();
        }
        return false;
    }

    private static boolean contains(Object left, String right) {
        if (left instanceof List<?> list) {
            return list.stream().anyMatch(item -> text(item).equals(right));
        }
        return text(left).contains(right);
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static double number(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException exception) {
            throw WorkflowSupport.invalid("比较的值不是数字: " + value);
        }
    }

    private static String preview(Object value) {
        String text = TemplateResolver.stringify(value);
        return text.length() <= PREVIEW_LENGTH ? text : text.substring(0, PREVIEW_LENGTH) + "…";
    }

    private static int elapsed(long startNanos) {
        return (int) ((System.nanoTime() - startNanos) / 1_000_000L);
    }

    private static String messageOf(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? "工作流执行失败" : message;
    }
}
