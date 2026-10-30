package com.hify.shared.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.hify.shared.json.TimeSerializers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** {@link JacksonConfig} 的单元测试。 */
class JacksonConfigTest {

    /** 按容器同样的方式构造 ObjectMapper：builder + 自定义器。 */
    private static ObjectMapper buildMapper() {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        new JacksonConfig().jacksonCustomizer().customize(builder);
        return builder.build();
    }

    @Test
    @DisplayName("LocalDateTime 输出 ISO 8601")
    void localDateTimeIsIso8601() throws Exception {
        ObjectMapper mapper = buildMapper();
        String json = mapper.writeValueAsString(LocalDateTime.of(2026, 10, 30, 15, 52, 42));
        assertThat(json).isEqualTo("\"2026-10-30T15:52:42\"");
    }

    @Test
    @DisplayName("LocalDateTime 截断到秒，不带毫秒")
    void localDateTimeDropsFraction() throws Exception {
        ObjectMapper mapper = buildMapper();
        String json = mapper.writeValueAsString(LocalDateTime.of(2026, 10, 30, 15, 52, 42, 123_000_000));
        assertThat(json).isEqualTo("\"2026-10-30T15:52:42\"");
    }

    @Test
    @DisplayName("LocalDate 输出 yyyy-MM-dd")
    void localDateIsPlainDay() throws Exception {
        ObjectMapper mapper = buildMapper();
        String json = mapper.writeValueAsString(LocalDate.of(2026, 10, 30));
        assertThat(json).isEqualTo("\"2026-10-30\"");
    }

    @Test
    @DisplayName("关掉了 WRITE_DATES_AS_TIMESTAMPS，时间不会写成数字时间戳")
    void writeDatesAsTimestampsIsDisabled() throws Exception {
        ObjectMapper mapper = buildMapper();
        assertThat(mapper.isEnabled(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)).isFalse();
    }

    @Test
    @DisplayName("时间模块同时覆盖 API 层与缓存层，两边格式一致")
    void apiAndCacheShareTheSameFormat() throws Exception {
        // 缓存用的是独立 mapper，但时间模块来自同一个工厂
        assertThat(TimeSerializers.LOCAL_DATE_TIME.format(LocalDateTime.of(2026, 10, 30, 15, 52, 42)))
                .isEqualTo("2026-10-30T15:52:42");
        assertThat(TimeSerializers.LOCAL_DATE.format(LocalDate.of(2026, 10, 30)))
                .isEqualTo("2026-10-30");
    }
}
