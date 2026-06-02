package com.zestflow.admin.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class AdminRedisConditionsTest {

    @Test
    void standaloneCaffeineDoesNotRequireRedis() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("zestflow.admin.deploy-mode", "standalone");
        env.setProperty("zestflow.admin.cache.type", "caffeine");
        assertThat(AdminRedisConditions.isRedisInfrastructureRequired(env)).isFalse();
    }

    @Test
    void clusterRequiresRedis() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("zestflow.admin.deploy-mode", "cluster");
        assertThat(AdminRedisConditions.isRedisInfrastructureRequired(env)).isTrue();
    }

    @Test
    void standaloneWithCacheRedisRequiresRedis() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("zestflow.admin.deploy-mode", "standalone");
        env.setProperty("zestflow.admin.cache.type", "redis");
        assertThat(AdminRedisConditions.isRedisInfrastructureRequired(env)).isTrue();
    }
}
