package com.zestflow.admin.runtime;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class AdminDeployModeConditionsTest {

    @Test
    void normalizeAliases() {
        assertThat(AdminDeployModeConditions.normalizeDeployMode("single")).isEqualTo("standalone");
        assertThat(AdminDeployModeConditions.normalizeDeployMode("multi")).isEqualTo("cluster");
        assertThat(AdminDeployModeConditions.normalizeDeployMode("cluster")).isEqualTo("cluster");
    }

    @Test
    void legacyRuntimeStateRedisMeansCluster() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("zestflow.admin.runtime-state.type", "redis");
        assertThat(new AdminDeployModeConditions.Cluster().matches(context(env), null)).isTrue();
    }

    @Test
    void explicitStandaloneIgnoresLegacyRedisTypeWhenDeployModeSet() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("zestflow.admin.deploy-mode", "standalone");
        env.setProperty("zestflow.admin.runtime-state.type", "redis");
        assertThat(new AdminDeployModeConditions.Cluster().matches(context(env), null)).isFalse();
    }

    private static ConditionContext context(Environment environment) {
        return new ConditionContext() {
            @Override
            public Environment getEnvironment() {
                return environment;
            }

            @Override
            public org.springframework.beans.factory.config.ConfigurableListableBeanFactory getRegistry() {
                return null;
            }

            @Override
            public org.springframework.core.io.ResourceLoader getResourceLoader() {
                return null;
            }

            @Override
            public ClassLoader getClassLoader() {
                return null;
            }
        };
    }
}
