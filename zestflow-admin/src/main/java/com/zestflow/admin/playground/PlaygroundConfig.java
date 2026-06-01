package com.zestflow.admin.playground;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 系统演示自动配置 — {@code zestflow.playground.enabled=true} 时生效
 */
@Configuration
@ConditionalOnProperty(prefix = "zestflow.playground", name = "enabled", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
public class PlaygroundConfig {

    @Value("${zestflow.playground.rate-limit:30}")
    private int defaultRateLimit;

    @Bean
    public PlaygroundRateLimiter playgroundRateLimiter() {
        return new PlaygroundRateLimiter(defaultRateLimit);
    }
}
