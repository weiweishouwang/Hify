package com.hify.app.service;

import com.hify.app.api.SettingsResp;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * 「设置」页的数据来源。
 *
 * <p>回答三个问题：<b>我跑的是哪个版本</b>、<b>依赖通不通</b>、<b>关键的运行参数是多少</b>。
 *
 * <p><b>只读</b>：一期没有需要用户在页面上改的配置。认证方案还没定（CLAUDE.md §五），
 * 也没有多租户与多环境切换 —— 有可改的东西再加写入接口。
 */
@Service
public class SettingsService {

    /** 构建信息文件的位置；由 spring-boot-maven-plugin 的 build-info goal 生成。 */
    private static final String BUILD_INFO = "META-INF/build-info.properties";

    private final ApplicationContext context;
    private final Environment environment;
    private final DataSource dataSource;
    private final StringRedisTemplate redis;

    @Value("${hify.vector.datasource.url:}") private String vectorUrl;
    @Value("${hify.vector.datasource.username:}") private String vectorUsername;
    @Value("${hify.vector.datasource.password:}") private String vectorPassword;

    public SettingsService(ApplicationContext context, Environment environment,
                           DataSource dataSource, StringRedisTemplate redis) {
        this.context = context;
        this.environment = environment;
        this.dataSource = dataSource;
        this.redis = redis;
    }

    /** 一次拿到全部设置项。 */
    public SettingsResp settings() {
        SettingsResp resp = new SettingsResp();
        resp.setApp(appInfo());
        resp.setDependencies(dependencies());
        resp.setParameters(parameters());
        return resp;
    }

    /** 应用信息：版本、构建时间、启动时间、运行时长、Java 版本、profile。 */
    private SettingsResp.AppInfo appInfo() {
        Properties build = buildInfo();
        SettingsResp.AppInfo info = new SettingsResp.AppInfo();
        info.setVersion(build.getProperty("build.version", "未知"));
        info.setBuildTime(build.getProperty("build.time"));
        // getStartupDate 返回的是毫秒时间戳，不是 Date
        LocalDateTime startedAt = LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(context.getStartupDate()), ZoneId.systemDefault());
        info.setStartedAt(startedAt);
        info.setUptimeSeconds(Duration.between(startedAt, LocalDateTime.now()).getSeconds());
        info.setJavaVersion(System.getProperty("java.version", "未知"));
        String[] profiles = environment.getActiveProfiles();
        info.setProfile(profiles == null || profiles.length == 0 ? "default" : String.join(", ", profiles));
        return info;
    }

    /**
     * 构建信息。
     *
     * <p>从 jar 里的 {@code META-INF/build-info.properties} 读 —— 版本号从包自身生成，
     * 不存在「代码里写的版本号」与「包里的版本号」两处漂移。
     */
    private Properties buildInfo() {
        Properties properties = new Properties();
        try (InputStream input = new ClassPathResource(BUILD_INFO).getInputStream()) {
            properties.load(input);
        } catch (IOException | RuntimeException exception) {
            // 没构建过（比如在 IDE 里直接跑）：版本号就不知道，不该因此让整个页面挂掉
        }
        return properties;
    }

    /** 三个依赖各探一次。探不通只影响这一行，不影响整页。 */
    private List<SettingsResp.DependencyStatus> dependencies() {
        List<SettingsResp.DependencyStatus> result = new ArrayList<>();
        result.add(probe("MySQL", this::probeDataSource));
        result.add(probe("PostgreSQL", this::probeVector));
        result.add(probe("Redis", this::probeRedis));
        return result;
    }

    private SettingsResp.DependencyStatus probe(String name, Probe probe) {
        SettingsResp.DependencyStatus status = new SettingsResp.DependencyStatus();
        status.setName(name);
        long start = System.nanoTime();
        try {
            probe.run();
            status.setUp(true);
            status.setDetail((System.nanoTime() - start) / 1_000_000L + " ms");
        } catch (Exception exception) {
            status.setUp(false);
            // 只说异常类型，不把连接串与口令带出去
            status.setDetail(exception.getClass().getSimpleName());
        }
        return status;
    }

    /** 业务库：容器里唯一的 DataSource 就是 MySQL（向量库刻意不暴露成 DataSource）。 */
    private String probeDataSource() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT 1")) {
            return resultSet.next() ? "1" : "0";
        }
    }

    /**
     * 向量库。
     *
     * <p>直接按应用自己的连接参数探一次 —— 不碰别的模块的内部 bean，
     * 也不复用知识库的数据源（那是有状态的连接池，探测不该占用它的连接）。
     */
    private String probeVector() throws Exception {
        if (vectorUrl == null || vectorUrl.isBlank()) {
            return "未配置";
        }
        try (Connection connection = DriverManager.getConnection(vectorUrl, vectorUsername, vectorPassword);
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT 1")) {
            return resultSet.next() ? "1" : "0";
        }
    }

    /** 缓存与协调：Redis。 */
    private String probeRedis() {
        String pong = redis.getConnectionFactory().getConnection().ping();
        return pong == null ? "" : pong;
    }

    /** 关键运行参数。写死在这里的是「看一眼就知道行为」的参数。 */
    private Map<String, String> parameters() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("熔断 · 滑动窗口", text("hify.resilience.circuit-breaker.sliding-window-size"));
        values.put("熔断 · 失败率阈值", text("hify.resilience.circuit-breaker.failure-rate-threshold") + "%");
        values.put("熔断 · 打开时长", text("hify.resilience.circuit-breaker.wait-duration-in-open-state"));
        values.put("熔断 · 半开试探次数", text("hify.resilience.circuit-breaker.permitted-number-of-calls-in-half-open-state"));
        values.put("LLM · 每用户每接入点在途上限", "3");
        values.put("LLM · 在途计数兜底过期", "120 秒");
        values.put("HTTP · 异步请求超时", text("spring.mvc.async.request-timeout"));
        values.put("HTTP · 优雅停机窗口", text("spring.lifecycle.timeout-per-shutdown-phase"));
        values.put("HTTP · Tomcat 线程数", text("server.tomcat.threads.max"));
        values.put("HTTP · 入站请求体上限", text("spring.codec.max-in-memory-size"));
        values.put("跨域 · 允许的来源", environment.getProperty("hify.cors.allowed-origins", "（未启用）"));
        return values;
    }

    private String text(String key) {
        return environment.getProperty(key, "—");
    }

    /** 一次依赖探测。 */
    @FunctionalInterface
    private interface Probe {
        String run() throws Exception;
    }
}
