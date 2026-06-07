package com.zestflow.admin.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * 非 prod 且 Flyway 开启时：启动前 repair → migrate。
 * Beta 单轨 V1 整合后，开发库 V1 checksum 或 history 漂移可自动对齐；prod 仍走严格校验。
 */
@Slf4j
@Configuration
@Profile("!prod")
@ConditionalOnProperty(name = "spring.flyway.enabled", havingValue = "true")
public class DemoFlywayConfig {

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        return flyway -> {
            log.info("[dev] Flyway 自动 repair + migrate");
            flyway.repair();
            flyway.migrate();
        };
    }
}
