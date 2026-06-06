package com.zestflow.demo.config;

import lombok.extern.slf4j.Slf4j;
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
 * 试玩/demo：日志库表结构自动补齐（Collector 独立数据源，不走主库 Flyway）。
 */
@Slf4j
@Configuration
@Profile("demo")
public class DemoCollectorSchemaConfig {

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 5)
    ApplicationRunner demoCollectorSchemaInitializer(
            @Qualifier("collectorDataSource") DataSource collectorDataSource) {
        return args -> {
            ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
            populator.addScript(new ClassPathResource("db/init.sql"));
            populator.setContinueOnError(true);
            log.info("[demo] Collector 日志库 schema 自动补齐");
            populator.execute(collectorDataSource);
        };
    }
}
