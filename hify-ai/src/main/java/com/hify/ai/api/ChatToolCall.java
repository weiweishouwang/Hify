package com.hify.ai.api;

/**
 * 模型发起的一次工具调用。
 *
 * @param id        调用标识，工具结果靠它回灌
 * @param name      工具名
 * @param arguments 入参，JSON 对象的字符串形式
 */
public record ChatToolCall(String id, String name, String arguments) {
}
