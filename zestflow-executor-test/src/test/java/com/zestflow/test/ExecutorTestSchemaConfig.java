package com.zestflow.test;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;

/**
 * 测试环境初始化独立数据源表结构（executorDataSource / collectorDataSource 不走 spring.sql.init）。
 */
@Configuration
@Profile("test")
public class ExecutorTestSchemaConfig {

    @Bean
    ResourceDatabasePopulator executorSchemaPopulator() {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.addScript(new ClassPathResource("schema.sql"));
        populator.setContinueOnError(false);
        return populator;
    }

    @Bean
    ResourceDatabasePopulator collectorSchemaPopulator() {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.addScript(new ClassPathResource("schema-log.sql"));
        populator.setContinueOnError(false);
        return populator;
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    ApplicationRunner executorSchemaInitializer(
            @Qualifier("executorDataSource") DataSource executorDataSource,
            ResourceDatabasePopulator executorSchemaPopulator) {
        return args -> executorSchemaPopulator.execute(executorDataSource);
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    ApplicationRunner collectorSchemaInitializer(
            @Qualifier("collectorDataSource") DataSource collectorDataSource,
            ResourceDatabasePopulator collectorSchemaPopulator) {
        return args -> collectorSchemaPopulator.execute(collectorDataSource);
    }
}
