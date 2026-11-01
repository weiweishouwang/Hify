package com.hify.knowledge.internal.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * 向量库的装配。
 *
 * <p>PG 侧一律走 {@link NamedParameterJdbcTemplate} 手写 SQL，<b>不用 MyBatis-Plus
 * 的 BaseMapper</b> —— pgvector 的 {@code <=>} 运算符与 HNSW 查询 MP 生成不了
 * （CLAUDE.md §3.3）。
 */
@Configuration
public class VectorDataSourceConfig {

    @Bean(destroyMethod = "close")
    public VectorDataSource vectorDataSource(
            @Value("${hify.vector.datasource.url}") String url,
            @Value("${hify.vector.datasource.username}") String username,
            @Value("${hify.vector.datasource.password}") String password,
            @Value("${hify.vector.datasource.driver-class-name:org.postgresql.Driver}") String driverClassName,
            @Value("${hify.vector.datasource.hikari.pool-name:hify-vector-pool}") String poolName,
            @Value("${hify.vector.datasource.hikari.maximum-pool-size:8}") int maximumPoolSize,
            @Value("${hify.vector.datasource.hikari.minimum-idle:1}") int minimumIdle,
            @Value("${hify.vector.datasource.hikari.connection-timeout:3000}") long connectionTimeoutMs,
            @Value("${hify.vector.datasource.hikari.max-lifetime:1700000}") long maxLifetimeMs) {
        HikariConfig config = new HikariConfig();
        config.setPoolName(poolName);
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName(driverClassName);
        config.setMaximumPoolSize(maximumPoolSize);
        config.setMinimumIdle(minimumIdle);
        config.setConnectionTimeout(connectionTimeoutMs);
        config.setMaxLifetime(maxLifetimeMs);
        return new VectorDataSource(new HikariDataSource(config));
    }

    @Bean
    public NamedParameterJdbcTemplate vectorJdbcTemplate(VectorDataSource vectorDataSource) {
        return vectorDataSource.jdbcTemplate();
    }
}
