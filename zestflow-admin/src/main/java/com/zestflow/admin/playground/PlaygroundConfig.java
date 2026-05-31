package com.zestflow.admin.playground;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Playground 自动配置 — {@code zestflow.playground.enabled=true} 时生效
 */
@Configuration
@ConditionalOnProperty(prefix = "zestflow.playground", name = "enabled", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
public class PlaygroundConfig {

    private final PlaygroundProperties properties;

    @Bean
    public PlaygroundRateLimiter playgroundRateLimiter() {
        return new PlaygroundRateLimiter(properties.getRateLimit());
    }
}
