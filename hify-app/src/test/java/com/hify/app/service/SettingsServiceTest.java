package com.hify.app.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 「设置」页的数据。
 *
 * <p>重点是「探不通一个依赖不能让整页挂掉」—— 依赖状态是给人看的，
 * 有一个不通恰恰是要显示出来的。
 */
@ExtendWith(MockitoExtension.class)
class SettingsServiceTest {

    @Mock ApplicationContext context;
    @Mock Environment environment;
    @Mock DataSource dataSource;
    @Mock StringRedisTemplate redis;

    private SettingsService service;

    @BeforeEach
    void setUp() {
        service = new SettingsService(context, environment, dataSource, redis);
        when(context.getStartupDate()).thenReturn(System.currentTimeMillis() - 60_000);
        when(environment.getProperty(anyString(), anyString())).thenReturn("—");
        lenient().when(environment.getActiveProfiles()).thenReturn(new String[0]);
    }

    /** 让 MySQL 与 Redis 探通。 */
    private void stubDependenciesUp() throws SQLException {
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        ResultSet resultSet = mock(ResultSet.class);
        lenient().when(dataSource.getConnection()).thenReturn(connection);
        lenient().when(connection.createStatement()).thenReturn(statement);
        lenient().when(statement.executeQuery(anyString())).thenReturn(resultSet);
        lenient().when(resultSet.next()).thenReturn(true);

        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        RedisConnection redisConnection = mock(RedisConnection.class);
        lenient().when(redis.getConnectionFactory()).thenReturn(factory);
        lenient().when(factory.getConnection()).thenReturn(redisConnection);
        lenient().when(redisConnection.ping()).thenReturn("PONG");
    }

    @Test
    @DisplayName("应用信息带版本、运行时长与 Java 版本")
    void appInfo() {
        when(environment.getActiveProfiles()).thenReturn(new String[] {"prod"});

        var app = service.settings().getApp();

        assertThat(app.getVersion()).isNotBlank();
        assertThat(app.getUptimeSeconds()).isGreaterThanOrEqualTo(60L);
        assertThat(app.getJavaVersion()).isNotBlank();
        assertThat(app.getProfile()).isEqualTo("prod");
    }

    @Test
    @DisplayName("三个依赖各探一次，全通时状态都是正常")
    void dependenciesUp() throws SQLException {
        stubDependenciesUp();

        var dependencies = service.settings().getDependencies();

        assertThat(dependencies).hasSize(3);
        assertThat(dependencies).extracting("name").containsExactly("MySQL", "PostgreSQL", "Redis");
        assertThat(dependencies).allSatisfy(dependency -> assertThat(dependency.getUp()).isTrue());
    }

    @Test
    @DisplayName("一个依赖探不通只影响它自己")
    void oneDependencyDown() throws SQLException {
        stubDependenciesUp();
        // MySQL 探不通：连接直接抛异常
        when(dataSource.getConnection()).thenThrow(new SQLException("连接被拒"));

        var dependencies = service.settings().getDependencies();

        assertThat(dependencies).hasSize(3);
        assertThat(dependencies.get(0).getUp()).isFalse();
        assertThat(dependencies.get(0).getDetail()).isEqualTo("SQLException");
        assertThat(dependencies.get(2).getUp()).isTrue();
    }

    @Test
    @DisplayName("运行参数列出熔断、超时与线程数")
    void parameters() {
        var parameters = service.settings().getParameters();

        assertThat(parameters).containsKeys(
                "熔断 · 滑动窗口", "熔断 · 失败率阈值",
                "HTTP · 异步请求超时", "HTTP · Tomcat 线程数");
        assertThat(parameters.get("LLM · 每用户每接入点在途上限")).isEqualTo("3");
    }
}
