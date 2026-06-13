package com.zestflow.admin.client.cache;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ExecutorReadCacheConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ExecutorReadCacheConfiguration.class))
            .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class));

    @Test
    void standalone_usesCaffeineCache() {
        runner.withPropertyValues(
                        "zestflow.admin.deploy-mode=standalone",
                        "zestflow.admin.executor-read-cache.enabled=true")
                .run(ctx -> assertThat(ctx.getBean(ExecutorReadCache.class))
                        .isInstanceOf(CaffeineExecutorReadCache.class));
    }

    @Test
    void cluster_usesRedisCache() {
        runner.withPropertyValues(
                        "zestflow.admin.deploy-mode=cluster",
                        "spring.data.redis.host=127.0.0.1",
                        "zestflow.admin.executor-read-cache.enabled=true")
                .run(ctx -> assertThat(ctx.getBean(ExecutorReadCache.class))
                        .isInstanceOf(RedisExecutorReadCache.class));
    }

    @Test
    void disabled_usesNoopCache() {
        runner.withPropertyValues("zestflow.admin.executor-read-cache.enabled=false")
                .run(ctx -> assertThat(ctx.getBean(ExecutorReadCache.class))
                        .isInstanceOf(NoopExecutorReadCache.class));
    }
}
