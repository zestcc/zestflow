package com.zestflow.executor.registry;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

/**
 * 执行器数据源自动装配
 * <p>
 * 支持两种模式：
 * <ol>
 *   <li>配置 zestflow.executor.datasource.url → 创建独立数据源</li>
 *   <li>未配置 → 复用主数据源</li>
 * </ol>
 */
@Configuration
public class ExecutorDataSourceConfig {

    // ==================== 独立数据源（可选） ====================

    @Bean
    @ConditionalOnProperty(prefix = "zestflow.executor.datasource", name = "url")
    public HikariDataSource executorDataSource(Environment env) {
        DataSourceProperties props = Binder.get(env)
                .bind("zestflow.executor.datasource", DataSourceProperties.class)
                .orElseGet(DataSourceProperties::new);
        return props.initializeDataSourceBuilder()
                .type(HikariDataSource.class).build();
    }

    // ==================== 复用主数据源（兜底） ====================

    @Bean("executorDataSource")
    @ConditionalOnMissingBean(name = "executorDataSource")
    public DataSource primaryDataSourceFallback(@Qualifier("dataSource") DataSource primaryDataSource) {
        return primaryDataSource;
    }

    // ==================== JdbcTemplate ====================

    @Bean
    public JdbcTemplate executorJdbcTemplate(@Qualifier("executorDataSource") DataSource ds) {
        return new JdbcTemplate(ds);
    }

    @Bean
    public PlatformTransactionManager executorTransactionManager(
            @Qualifier("executorDataSource") DataSource ds) {
        return new DataSourceTransactionManager(ds);
    }
}
