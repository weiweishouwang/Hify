package com.hify.ai.internal.spi;

import com.hify.ai.api.ChatToolCall;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具调用参数的拼装。
 *
 * <p><b>上游的工具调用参数是分片到达的</b>：OpenAI 的 {@code delta.tool_calls[].function.arguments}
 * 是一段 JSON 字符串的增量，Anthropic 的 {@code input_json_delta} 同理。
 * 这里把分片按序号拼完整，<b>调用方拿到的一定是完整的 JSON 字符串</b>，
 * 分片不泄漏到编排层。
 *
 * <p>序号用 {@code index}（OpenAI 给）或块序号（Anthropic 给）。有的协议
 * （Ollama 原生）一次性给全���参数 —— 那就直接整段塞进来。
 */
final class ToolCallAccumulator {

    private final Map<Integer, String> ids = new LinkedHashMap<>();
    private final Map<Integer, String> names = new LinkedHashMap<>();
    private final Map<Integer, StringBuilder> arguments = new LinkedHashMap<>();
    private final Map<Integer, Integer> orders = new LinkedHashMap<>();
    private int sequence;

    /**
     * 追加一段参数分片。
     *
     * @param index         序号；为空时按出现顺序自动分配
     * @param id            调用标识（第一次出现时给）
     * @param name          工具名（第一次出现时给）
     * @param argumentDelta 参数分片，可以为空
     */
    void append(Integer index, String id, String name, String argumentDelta) {
        int key = index == null ? nextIndex() : index;
        orders.putIfAbsent(key, sequence++);
        if (id != null && !id.isBlank()) {
            ids.put(key, id);
        }
        if (name != null && !name.isBlank()) {
            names.put(key, name);
        }
        if (argumentDelta != null && !argumentDelta.isEmpty()) {
            arguments.computeIfAbsent(key, k -> new StringBuilder()).append(argumentDelta);
        }
    }

    /**
     * 追加一次完整的工具调用（参数不是分片）。
     *
     * @param index     序号；为空时按出现顺序自动分配
     * @param id        调用标识；为空时自己生成
     * @param name      工具名
     * @param arguments 参数（JSON 字符串）
     */
    void appendWhole(Integer index, String id, String name, String arguments) {
        int key = index == null ? nextIndex() : index;
        orders.putIfAbsent(key, sequence++);
        if (id != null && !id.isBlank()) {
            ids.put(key, id);
        }
        if (name != null && !name.isBlank()) {
            names.put(key, name);
        }
        this.arguments.computeIfAbsent(key, k -> new StringBuilder()).append(arguments);
    }

    /** 自动分配的序号：避开已经用过的下标。 */
    private int nextIndex() {
        int candidate = sequence;
        while (orders.containsKey(candidate)) {
            candidate++;
        }
        return candidate;
    }

    /**
     * 取出并清空已拼好的工具调用。
     *
     * @return 按出现顺序排列的工具调用；没有则是空列表
     */
    List<ChatToolCall> drain() {
        List<Integer> indexes = new ArrayList<>(orders.keySet());
        indexes.sort((left, right) -> Integer.compare(orders.get(left), orders.get(right)));
        List<ChatToolCall> calls = new ArrayList<>(indexes.size());
        for (int index : indexes) {
            String id = ids.get(index);
            String name = names.get(index);
            StringBuilder argument = arguments.get(index);
            calls.add(new ChatToolCall(
                    id == null ? "call_" + index : id,
                    name == null ? "" : name,
                    argument == null ? "{}" : argument.toString()));
        }
        ids.clear();
        names.clear();
        arguments.clear();
        orders.clear();
        sequence = 0;
        return calls;
    }
}
