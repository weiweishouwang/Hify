package com.hify.tool.internal.spi;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 获取当前 UTC 时间。
 *
 * <p>一期唯一一个内置工具，用来验证「按 sourceType 分发到对应 provider」这条链路
 * 真的跑得通 —— 只做 HTTP 来源的话，这条逻辑一行真实用例都没有。
 *
 * <p>无副作用、不访问外部资源，因此可以放心地被模型反复调用。
 */
@Component
public class BuiltinTimeTool implements BuiltinTool {

    @Override
    public String toolKey() {
        return "current_time";
    }

    @Override
    public String name() {
        return "current_time";
    }

    @Override
    public String description() {
        return "获取当前的 UTC 时间，返回 ISO 8601 格式，例如 2026-10-31T15:52:42Z。需要知道「现在几点」时使用。";
    }

    @Override
    public Map<String, Object> schema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(),
                "additionalProperties", false);
    }

    @Override
    public String execute(Map<String, Object> args) {
        return DateTimeFormatter.ISO_INSTANT.format(Instant.now());
    }
}
