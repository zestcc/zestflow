package com.zestflow.test.config;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.flyway.FlywayProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * 日志数据源 Flyway 配置
 * <p>
 * 主数据源的 Flyway 已在 application.yml 中关闭（spring.flyway.enabled=false），
 * 日志数据源通过手动创建 Flyway 实例来迁移 chain_event 表。
 */
@Configuration
public class FlywayConfig {

    /**
     * 日志数据源的 Flyway 配置属性
     */
    @Bean
    @ConfigurationProperties(prefix = "zestflow.log.flyway")
    public FlywayProperties logFlywayProperties() {
        return new FlywayProperties();
    }

    /**
     * 日志数据源的 Flyway 迁移
     */
    @Bean(initMethod = "migrate")
    public Flyway logFlyway(@Qualifier("logDataSource") DataSource logDataSource,
                             FlywayProperties logFlywayProperties) {
        return Flyway.configure()
                .dataSource(logDataSource)
                .locations(logFlywayProperties.getLocations().toArray(new String[0]))
                .baselineOnMigrate(logFlywayProperties.isBaselineOnMigrate())
                .baselineVersion(logFlywayProperties.getBaselineVersion())
                .table(logFlywayProperties.getTable())
                .load();
    }
}
