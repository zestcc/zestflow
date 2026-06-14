package com.zestflow.demo.config;

import com.zestflow.demo.config.flyway.DemoFlywayPolicies;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * demo / strictv1-e2e profile：Executor 业务库 Flyway（策略与 Admin 非 prod 一致，见 docs/FLYWAY_POLICY.md）。
 */
@Configuration
@Profile({"demo", "strictv1-e2e"})
public class DemoFlywayConfig {

    @Bean
    public FlywayConfigurationCustomizer demoFlywayCustomizer() {
        return DemoFlywayPolicies::applyNonProductionPolicy;
    }

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        return flyway -> DemoFlywayPolicies.migrateNonProduction(flyway, "demo-executor");
    }
}
