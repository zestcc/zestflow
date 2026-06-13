package com.zestflow.admin.client.cache;

import com.zestflow.admin.config.AdminRedisConditions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Executor 读快照装配 — standalone Caffeine / cluster Redis / 关闭 Noop。
 */
@Configuration
@EnableConfigurationProperties(ExecutorReadCacheProperties.class)
public class ExecutorReadCacheConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "zestflow.admin.executor-read-cache", name = "enabled", havingValue = "false")
    ExecutorReadCache noopExecutorReadCache() {
        return new NoopExecutorReadCache();
    }

    @Bean
    @Conditional(ClusterExecutorReadCacheCondition.class)
    ExecutorReadCache redisExecutorReadCache(StringRedisTemplate redisTemplate, ExecutorReadCacheProperties properties) {
        return new RedisExecutorReadCache(redisTemplate, properties);
    }

    static final class ClusterExecutorReadCacheCondition implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            Environment env = context.getEnvironment();
            if (!AdminRedisConditions.isRedisInfrastructureRequired(env)) {
                return false;
            }
            if (!"cluster".equalsIgnoreCase(env.getProperty("zestflow.admin.deploy-mode"))) {
                return false;
            }
            return !"false".equalsIgnoreCase(env.getProperty("zestflow.admin.executor-read-cache.enabled", "true"));
        }
    }

    @Bean
    @ConditionalOnProperty(prefix = "zestflow.admin.executor-read-cache", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    @ConditionalOnMissingBean(ExecutorReadCache.class)
    ExecutorReadCache caffeineExecutorReadCache(ExecutorReadCacheProperties properties) {
        return new CaffeineExecutorReadCache(properties);
    }
}
