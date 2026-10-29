package com.hify.shared.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.shared.exception.BizException;
import com.hify.shared.exception.ErrorCode;
import org.springframework.stereotype.Component;

/**
 * JSON 序列化工具。
 *
 * <p>**刻意不自己 new ObjectMapper**，而是复用容器里 Spring Boot 自动配置的那个实例
 * （{@code JacksonAutoConfiguration}）。原因：自己 new 出来的 mapper 不会自动注册
 * Spring Boot 注册的那些模块（如 {@code JavaTimeModule}），同一个对象在两处会被
 * 序列化成不同的 JSON——这类分叉极难排查。复用容器实例则与 HTTP 接口返回的
 * 序列化行为天然一致。
 *
 * <p>用法：注入本类（{@code @Resource} 或构造注入），不要静态调用。
 */
@Component
public class JsonUtils {

    private final ObjectMapper objectMapper;

    public JsonUtils(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 容器里的 ObjectMapper 实例。需要用到它自身的 API（如注册模块）时取这个。
     */
    public ObjectMapper mapper() {
        return objectMapper;
    }

    /**
     * 序列化为 JSON 字符串。
     *
     * @param value 待序列化对象
     * @return JSON 字符串
     * @throws BizException 序列化失败时抛出（错误码 INTERNAL_ERROR——是我们的数据有问题）
     */
    public String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "序列化失败: " + e.getOriginalMessage(), e);
        }
    }

    /**
     * 反序列化为指定类型。
     *
     * @param json JSON 字符串
     * @param type 目标类型
     * @return 反序列化结果
     * @throws BizException JSON 非法或类型不匹配时抛出（错误码 PARAM_ERROR——是入参有问题）
     */
    public <T> T fromJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.PARAM_ERROR, "JSON 解析失败: " + e.getOriginalMessage(), e);
        }
    }

    /**
     * 反序列化为泛型类型。
     *
     * @param json JSON 字符串
     * @param type 带泛型信息的类型引用
     * @return 反序列化结果
     * @throws BizException JSON 非法或类型不匹配时抛出
     */
    public <T> T fromJson(String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.PARAM_ERROR, "JSON 解析失败: " + e.getOriginalMessage(), e);
        }
    }

    /**
     * 反序列化为 JavaType。
     *
     * @param json JSON 字符串
     * @param type 由 {@code objectMapper.getTypeFactory()} 构造的类型
     * @return 反序列化结果
     * @throws BizException JSON 非法或类型不匹配时抛出
     */
    public <T> T fromJson(String json, JavaType type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.PARAM_ERROR, "JSON 解析失败: " + e.getOriginalMessage(), e);
        }
    }

    /**
     * 解析为 Map，便于读取动态结构的 JSON（如外部 MCP server 的配置）。
     *
     * @param json JSON 字符串
     * @return Map 形式的结果
     * @throws BizException JSON 非法时抛出
     */
    public java.util.Map<String, Object> toMap(String json) {
        return fromJson(json, new TypeReference<java.util.Map<String, Object>>() { });
    }
}
