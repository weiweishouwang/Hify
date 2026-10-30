package com.hify.shared.json;

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 时间类型的 JSON 序列化约定。
 *
 * <p><b>LocalDateTime 用 ISO 8601</b>（{@code 2026-10-30T15:52:42}），不用空格分隔：
 * 带 {@code T} 的格式能一眼看出这是日期时间而不是别的什么，前端 {@code new Date(...)}
 * 与 {@code moment} 也能直接解析。
 *
 * <p><b>不保留毫秒</b>：对外接口不需要毫秒精度，库里是 {@code DATETIME(3)} 但
 * API 层统一截断到秒，避免同一份数据在不同接口里出现两种精度。
 *
 * <p>时间一律按 UTC 理解（{@link LocalDateTime} 本身不带时区），由 API 层负责
 * 带上时区偏移再返回给前端。
 */
public final class TimeSerializers {

    /** LocalDateTime 的输出格式，ISO 8601。 */
    public static final DateTimeFormatter LOCAL_DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    /** LocalDate 的输出格式。 */
    public static final DateTimeFormatter LOCAL_DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private TimeSerializers() {
    }

    /**
     * 构造带自定义时间格式的 {@link JavaTimeModule}。
     *
     * <p>API 层的 ObjectMapper 与缓存用的 ObjectMapper 都用这个方法，
     * 两边的时间格式才不会分叉。
     *
     * @return 时间模块
     */
    public static JavaTimeModule timeModule() {
        JavaTimeModule module = new JavaTimeModule();
        module.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(LOCAL_DATE_TIME));
        module.addSerializer(LocalDate.class, new LocalDateSerializer(LOCAL_DATE));
        return module;
    }
}
