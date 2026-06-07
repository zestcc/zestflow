package com.zestflow.admin.config.flyway;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * 非 prod Flyway：启动时 repair + 允许 out-of-order 补跑，避免开发库升级 jar 后无法启动。
 */
@Configuration
@Profile("!prod")
@ConditionalOnProperty(name = "spring.flyway.enabled", havingValue = "true")
public class NonProductionFlywayConfiguration {

    @Bean
    public FlywayConfigurationCustomizer nonProductionFlywayCustomizer() {
        return ZestFlowFlywayPolicies::applyNonProductionPolicy;
    }

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        return flyway -> ZestFlowFlywayPolicies.migrateNonProduction(flyway, "admin");
    }
}
