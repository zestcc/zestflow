package com.zestflow.admin.config.flyway;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * prod Flyway：严格顺序 + validate-on-migrate；配合 {@link AdminProductionGuard} 启动校验。
 */
@Configuration
@Profile("prod")
@ConditionalOnProperty(name = "spring.flyway.enabled", havingValue = "true")
public class ProductionFlywayConfiguration {

    @Bean
    public FlywayConfigurationCustomizer productionFlywayCustomizer() {
        return ZestFlowFlywayPolicies::applyProductionPolicy;
    }
}
