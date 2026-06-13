package com.zestflow.admin.system;

import com.zestflow.admin.config.PlaygroundPlatformConfig;
import com.zestflow.admin.runtime.AdminDeployProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductionReadinessServiceTest {

    @Mock
    private PlaygroundPlatformConfig playgroundPlatformConfig;

    @Mock
    private AdminDeployProperties deployProperties;

    @Test
    void evaluate_devProfile_reportsTokenGaps() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("local");
        ProductionReadinessService service = new ProductionReadinessService(env, playgroundPlatformConfig, deployProperties);
        when(playgroundPlatformConfig.isEnabled()).thenReturn(true);
        when(deployProperties.getDeployMode()).thenReturn("standalone");

        Map<String, Object> result = service.evaluate();

        assertThat(result.get("prodProfile")).isEqualTo(false);
        assertThat(result.get("ready")).isEqualTo(false);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
        assertThat(items).extracting(i -> i.get("name")).contains("registry-token", "jwt.secret");
    }

    @Test
    void evaluate_prodProfile_passesWhenSecretsConfigured() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        env.setProperty("zestflow.admin.registry-token", "prod-registry-secret-ok");
        env.setProperty("zestflow.admin.executor-access-token", "prod-executor-secret-ok");
        env.setProperty("zestflow.collector.access-token", "prod-collector-secret-ok");
        env.setProperty("zestflow.jwt.secret", "01234567890123456789012345678901");
        env.setProperty("zestflow.sso.enabled", "false");
        ProductionReadinessService service = new ProductionReadinessService(env, playgroundPlatformConfig, deployProperties);
        when(playgroundPlatformConfig.isEnabled()).thenReturn(false);
        when(deployProperties.getDeployMode()).thenReturn("standalone");

        Map<String, Object> result = service.evaluate();

        assertThat(result.get("prodProfile")).isEqualTo(true);
        assertThat(result.get("ready")).isEqualTo(true);
    }
}
