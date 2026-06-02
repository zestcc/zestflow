package com.zestflow.admin.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Admin 业务缓存 — {@code cache.type} 独立控制；单机默认 caffeine，无需 Redis。
 */
@Slf4j
@Configuration
@EnableCaching
@RequiredArgsConstructor
public class AdminCacheConfig {

    public static final String PERMISSIONS_CACHE = "permissions";

    private final AdminCacheProperties cacheProperties;

    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "zestflow.admin.cache", name = "type", havingValue = "caffeine", matchIfMissing = true)
    public CacheManager caffeineCacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(PERMISSIONS_CACHE);

        Caffeine<Object, Object> builder = Caffeine.newBuilder()
                .maximumSize(cacheProperties.getMaximumSize())
                .expireAfterWrite(cacheProperties.getTtlSeconds(), TimeUnit.SECONDS);

        if (cacheProperties.isRecordStats()) {
            builder.recordStats();
        }

        manager.setCaffeine(builder);
        manager.setAllowNullValues(true);

        log.info("权限缓存 Caffeine maximumSize={} ttl={}s", cacheProperties.getMaximumSize(),
                cacheProperties.getTtlSeconds());
        return manager;
    }

    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "zestflow.admin.cache", name = "type", havingValue = "simple")
    public CacheManager simpleCacheManager() {
        log.info("权限缓存 ConcurrentMap（simple 模式）");
        return new ConcurrentMapCacheManager(PERMISSIONS_CACHE);
    }

    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "zestflow.admin.cache", name = "type", havingValue = "redis")
    public CacheManager redisCacheManager(RedisConnectionFactory adminSharedRedisConnectionFactory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(cacheProperties.getTtlSeconds()))
                .disableCachingNullValues();

        RedisCacheManager manager = RedisCacheManager.builder(adminSharedRedisConnectionFactory)
                .cacheDefaults(config)
                .build();

        log.info("权限缓存 Redis ttl={}s", cacheProperties.getTtlSeconds());
        return manager;
    }
}
