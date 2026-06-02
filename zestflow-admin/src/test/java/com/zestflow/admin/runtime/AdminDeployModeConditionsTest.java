package com.zestflow.admin.runtime;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
        ConditionContext context = mock(ConditionContext.class);
        when(context.getEnvironment()).thenReturn(environment);
        return context;
    }
}
