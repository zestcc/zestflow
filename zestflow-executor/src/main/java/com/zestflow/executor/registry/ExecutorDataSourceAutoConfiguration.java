package com.zestflow.executor.registry;

import com.zestflow.collector.support.ZestFlowDataSourcePropertiesResolver;
import com.zestflow.collector.support.ZestFlowDataSourcePropertiesResolver.JdbcConnectionSettings;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

/**
 * 执行器 JDBC 栈 — 始终独立 {@code executorDataSource}；同库/分库仅 JDBC URL 不同。
 */
@AutoConfiguration
public class ExecutorDataSourceAutoConfiguration {

    private static final String DATASOURCE_PREFIX = "zestflow.executor.datasource";

    @Bean(name = "executorDataSource")
    HikariDataSource executorDataSource(Environment env) {
        JdbcConnectionSettings settings = ZestFlowDataSourcePropertiesResolver.resolve(
                env, DATASOURCE_PREFIX, "Executor");
        return ZestFlowDataSourcePropertiesResolver.createHikariDataSource(settings);
    }

    @Bean(name = "executorJdbcTemplate")
    JdbcTemplate executorJdbcTemplate(@Qualifier("executorDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean(name = "executorTransactionManager")
    PlatformTransactionManager executorTransactionManager(
            @Qualifier("executorDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}
