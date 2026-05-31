package com.zestflow.admin.demo;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 系统演示自动配置 — {@code zestflow.demo.enabled=true} 时生效
 */
@Configuration
@ConditionalOnProperty(prefix = "zestflow.demo", name = "enabled", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
public class DemoConfig {

    @Value("${zestflow.demo.rate-limit:30}")
    private int defaultRateLimit;

    @Bean
    public DemoRateLimiter demoRateLimiter() {
        return new DemoRateLimiter(defaultRateLimit);
    }
}
