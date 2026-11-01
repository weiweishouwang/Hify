package com.hify.conversation.internal.service;

import com.hify.agent.api.AgentBundle;
import com.hify.ai.api.ChatTool;
import com.hify.tool.api.ToolSpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 工具函数名的清洗与映射。 */
class ToolFunctionNamesTest {

    static ToolSpec spec(String sourceType, String toolKey, String name) {
        return new ToolSpec(toolKey, name, "描述", Map.of("type", "object"), Map.of(), sourceType);
    }

    AgentBundle bundle(ToolSpec... specs) {
        var model = new com.hify.ai.api.ModelSummaryResp();
        model.setPublicId("01MODEL0000000000000000001");
        return new AgentBundle("01AGENT00000000000000000001", "客服", "published", "提示词",
                model, List.of(specs), List.of(), null);
    }

    @Test
    @DisplayName("ASCII 工具标识原样做函数名")
    void asciiToolKeyKept() {
        var names = ToolFunctionNames.of(bundle(spec("builtin", "current_time", "当前时间")));
        assertThat(names.chatTools()).hasSize(1);
        ChatTool tool = names.chatTools().getFirst();
        assertThat(tool.name()).isEqualTo("current_time");
        assertThat(tool.description()).isEqualTo("描述");
    }

    @Test
    @DisplayName("中文工具标识清洗成合法函数名")
    void nonAsciiSanitized() {
        var names = ToolFunctionNames.of(bundle(spec("http", "查订单", "查订单")));
        assertThat(names.chatTools().getFirst().name()).isEqualTo("tool");
        assertThat(names.chatTools().getFirst().name()).matches("[A-Za-z0-9_-]{1,64}");
    }

    @Test
    @DisplayName("以数字开头的工具标识加前缀")
    void digitPrefixFixed() {
        var names = ToolFunctionNames.of(bundle(spec("http", "2fa", "双因子")));
        assertThat(names.chatTools().getFirst().name()).isEqualTo("t_2fa");
    }

    @Test
    @DisplayName("同名工具不会撞名")
    void uniqueNames() {
        var names = ToolFunctionNames.of(
                bundle(spec("http", "order", "订单"), spec("mcp", "order", "订单")));
        List<String> functionNames = names.chatTools().stream().map(ChatTool::name).toList();
        assertThat(functionNames).containsExactly("order", "order_2");
    }

    @Test
    @DisplayName("工具标识与函数名一一对应，回放历史时能找回")
    void toolKeyMapping() {
        var names = ToolFunctionNames.of(bundle(spec("builtin", "current_time", "当前时间")));
        assertThat(names.functionNameOf("builtin:current_time")).isEqualTo("current_time");
        assertThat(names.specOf("current_time").toolKey()).isEqualTo("current_time");
        assertThat(names.specOf("不存在")).isNull();
    }

    @Test
    @DisplayName("函数名长度不超过协议上限")
    void nameLengthCapped() {
        String longKey = "a".repeat(200);
        var names = ToolFunctionNames.of(bundle(spec("http", longKey, "长名字")));
        assertThat(names.chatTools().getFirst().name().length()).isLessThanOrEqualTo(64);
    }
}
