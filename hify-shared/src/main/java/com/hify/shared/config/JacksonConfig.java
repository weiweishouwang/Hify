package com.hify.shared.config;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.hify.shared.json.TimeSerializers;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson 全局配置。
 *
 * <p>时间统一成 ISO 8601（{@code LocalDateTime} → {@code 2026-10-30T15:52:42}，
 * {@code LocalDate} → {@code 2026-10-30}），并关掉 {@code WRITE_DATES_AS_TIMESTAMPS}
 * ——那个开关开着的话时间会被写成数字时间戳，前端还得再转一次。
 *
 * <p>用 {@link Jackson2ObjectMapperBuilderCustomizer} 而不是自己 new 一个
 * {@code ObjectMapper}：容器里的那个对象是全局唯一的，{@code JsonUtils} 也包着它，
 * 另起一个 mapper 会让「同一个字段在不同接口里格式不一样」。
 */
@Configuration(proxyBeanMethods = false)
public class JacksonConfig {

    /**
     * 时间格式与开关。
     *
     * <p>注意 {@code modulesToInstall} 的时序：Spring Boot 先装默认的
     * {@code JavaTimeModule}，再跑自定义器，所以这里装的自定义模块后注册、覆盖前者。
     *
     * @return Jackson 自定义器
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        return builder -> {
            builder.modulesToInstall(TimeSerializers.timeModule());
            builder.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        };
    }
}
