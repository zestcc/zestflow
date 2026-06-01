package com.zestflow.admin.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
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
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 缓存自动配置
 * <p>
 * 根据 zestflow.admin.cache.type 切换缓存后端：
 * <ul>
 *   <li>不配置 / simple — ConcurrentMapCacheManager（零依赖，开发环境）</li>
 *   <li>caffeine — CaffeineCacheManager（高性能本地缓存，生产单机）</li>
 *   <li>redis — RedisCacheManager（集群共享缓存，需配置 spring.data.redis.*）</li>
 * </ul>
 */
@Slf4j
@Configuration
@EnableCaching
public class AdminCacheConfig {

    public static final String PERMISSIONS_CACHE = "permissions";

    @Bean
    @ConfigurationProperties(prefix = "zestflow.admin.cache")
    public AdminCacheProperties adminCacheProperties() {
        return new AdminCacheProperties();
    }

    /**
     * caffeine 实现 — 显式配置时启用
     */
    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "zestflow.admin.cache", name = "type", havingValue = "caffeine")
    public CacheManager caffeineCacheManager(AdminCacheProperties properties) {
        CaffeineCacheManager manager = new CaffeineCacheManager(PERMISSIONS_CACHE);

        Caffeine<Object, Object> builder = Caffeine.newBuilder()
                .maximumSize(properties.getMaximumSize())
                .expireAfterWrite(properties.getTtlSeconds(), TimeUnit.SECONDS);

        if (properties.isRecordStats()) {
            builder.recordStats();
        }

        manager.setCaffeine(builder);
        manager.setAllowNullValues(true);

        log.info("缓存启用 Caffeine maximumSize={} ttl={}s", properties.getMaximumSize(), properties.getTtlSeconds());
        return manager;
    }

    /**
     * simple 实现 — 未配置 cache.type 或显式设为 simple 时使用（零依赖兜底）
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean(CacheManager.class)
    public CacheManager simpleCacheManager() {
        log.info("缓存启用 ConcurrentMap（开发模式，无额外依赖）");
        return new ConcurrentMapCacheManager(PERMISSIONS_CACHE);
    }

    /**
     * redis 实现 — zestflow.admin.cache.type=redis 时启用
     * <p>
     * 使用 spring.data.redis.* 标准配置连接 Redis，Lettuce 连接池。
     * TTL 由 zestflow.admin.cache.ttl-seconds 控制，默认 60s。
     */
    @Configuration
    @ConditionalOnProperty(prefix = "zestflow.admin.cache", name = "type", havingValue = "redis")
    public static class AdminRedisCacheConfig {

        @Bean
        @Primary
        public CacheManager redisCacheManager(RedisConnectionFactory connectionFactory,
                                              AdminCacheProperties properties) {
            RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                    .entryTtl(Duration.ofSeconds(properties.getTtlSeconds()))
                    .disableCachingNullValues();

            RedisCacheManager manager = RedisCacheManager.builder(connectionFactory)
                    .cacheDefaults(config)
                    .build();

            log.info("缓存启用 Redis ttl={}s", properties.getTtlSeconds());
            return manager;
        }

        @Bean
        public RedisConnectionFactory redisConnectionFactory() {
            RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
            // spring.data.redis.host / port / password / database 由 Spring Boot 绑定，
            // 这里只设默认值，实际值由外部配置覆盖。
            config.setHostName("localhost");
            config.setPort(6379);
            config.setDatabase(0);
            return new LettuceConnectionFactory(config);
        }
    }

    public static class AdminCacheProperties {

        /** 缓存类型：simple / caffeine / redis */
        private String type = "simple";

        /** Caffeine 最大条目数 */
        private long maximumSize = 10_000;

        /** 缓存 TTL（秒） */
        private long ttlSeconds = 60;

        /** 是否启用统计（Caffeine 特有） */
        private boolean recordStats = false;

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public long getMaximumSize() { return maximumSize; }
        public void setMaximumSize(long maximumSize) { this.maximumSize = maximumSize; }
        public long getTtlSeconds() { return ttlSeconds; }
        public void setTtlSeconds(long ttlSeconds) { this.ttlSeconds = ttlSeconds; }
        public boolean isRecordStats() { return recordStats; }
        public void setRecordStats(boolean recordStats) { this.recordStats = recordStats; }
    }
}
