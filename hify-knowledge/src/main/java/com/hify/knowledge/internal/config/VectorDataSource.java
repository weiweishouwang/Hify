package com.hify.knowledge.internal.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;

/**
 * 向量库（PostgreSQL + pgvector）的连接池。
 *
 * <p><b>刻意不作为 {@code DataSource} 类型的 bean 暴露</b>：
 * Spring Boot 的 {@code DataSourceAutoConfiguration} 带
 * {@code @ConditionalOnMissingBean(DataSource.class)} —— 容器里只要出现任何
 * {@code DataSource} bean，它就不再生成 MySQL 的数据源，MyBatis-Plus 就会连到 PG 去。
 *
 * <p>所以这里用一个包装类型持有连接池：MySQL 侧照旧由 Boot 自动配置接管，
 * PG 侧的池仍然受 Spring 生命周期管理（{@code close} 时关闭）。
 */
public class VectorDataSource implements AutoCloseable {

    private final HikariDataSource dataSource;

    public VectorDataSource(HikariDataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** 供 {@code NamedParameterJdbcTemplate} 使用。 */
    public DataSource dataSource() {
        return dataSource;
    }

    /** 每次调用新建一个模板 —— 模板本身无状态，共享一个实例即可，这里保持简单。 */
    public NamedParameterJdbcTemplate jdbcTemplate() {
        return new NamedParameterJdbcTemplate(dataSource);
    }

    @Override
    public void close() {
        dataSource.close();
    }
}
