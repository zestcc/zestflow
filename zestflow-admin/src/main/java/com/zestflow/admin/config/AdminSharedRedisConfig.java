package com.zestflow.admin.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;

/**
 * 按需创建 Redis 连接 — 单机 standalone + caffeine 时不加载此配置类。
 */
@Slf4j
@Configuration
@Conditional(AdminRedisConditions.InfrastructureRequired.class)
@EnableConfigurationProperties(RedisProperties.class)
public class AdminSharedRedisConfig {

    @Bean(name = "adminSharedRedisConnectionFactory")
    public LettuceConnectionFactory adminSharedRedisConnectionFactory(RedisProperties redisProperties) {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(StringUtils.hasText(redisProperties.getHost()) ? redisProperties.getHost() : "localhost");
        config.setPort(redisProperties.getPort());
        config.setDatabase(redisProperties.getDatabase());
        if (StringUtils.hasText(redisProperties.getPassword())) {
            config.setPassword(redisProperties.getPassword());
        }
        log.info("Admin Redis 连接就绪 host={}:{} db={}", config.getHostName(), config.getPort(), config.getDatabase());
        return new LettuceConnectionFactory(config);
    }

    @Bean(name = "adminRuntimeStringRedisTemplate")
    public StringRedisTemplate adminRuntimeStringRedisTemplate(RedisConnectionFactory adminSharedRedisConnectionFactory) {
        return new StringRedisTemplate(adminSharedRedisConnectionFactory);
    }
}
