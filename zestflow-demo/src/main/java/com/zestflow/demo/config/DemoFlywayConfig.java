package com.zestflow.demo.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * 试玩/demo：业务库 Flyway 启动时自动 repair + migrate（补 chain_key 等增量 DDL）。
 */
@Slf4j
@Configuration
@Profile("demo")
public class DemoFlywayConfig {

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        return flyway -> {
            log.info("[demo] Executor 业务库 Flyway 自动 repair + migrate");
            flyway.repair();
            flyway.migrate();
        };
    }
}
