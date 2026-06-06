package com.zestflow.admin.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * 试玩/demo 环境：每次启动自动 repair → migrate，升级 jar 无需手改 flyway_schema_history。
 * prod 仍走默认严格校验（validate-on-migrate=true）。
 */
@Slf4j
@Configuration
@Profile("demo")
public class DemoFlywayConfig {

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        return flyway -> {
            log.info("[demo] Flyway 自动 repair + migrate（无需手动改 schema history）");
            flyway.repair();
            flyway.migrate();
        };
    }
}
