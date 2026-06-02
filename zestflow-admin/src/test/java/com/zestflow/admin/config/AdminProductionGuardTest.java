package com.zestflow.admin.config;

import com.zestflow.admin.runtime.AdminDeployProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminProductionGuardTest {

    @Test
    void validateProductionConfig_standaloneWithSecrets_passes() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("zestflow.admin.registry-token", "prod-registry-secret");
        env.setProperty("zestflow.jwt.secret", "prod-jwt-secret-value");

        AdminDeployProperties deploy = new AdminDeployProperties();
        deploy.setDeployMode("standalone");

        AdminProductionGuard guard = new AdminProductionGuard(env, deploy);
        assertThatCode(guard::validateProductionConfig).doesNotThrowAnyException();
    }

    @Test
    void validateProductionConfig_missingRegistryToken_fails() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("zestflow.jwt.secret", "prod-jwt-secret-value");

        AdminDeployProperties deploy = new AdminDeployProperties();
        deploy.setDeployMode("standalone");

        AdminProductionGuard guard = new AdminProductionGuard(env, deploy);
        assertThatThrownBy(guard::validateProductionConfig)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("生产环境配置不完整");
    }

    @Test
    void validateProductionConfig_defaultJwtSecret_fails() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("zestflow.admin.registry-token", "prod-registry-secret");
        env.setProperty("zestflow.jwt.secret", "ZestFlow_dev_JWT_Secret_Key_Change_Me_In_Production_!!_");

        AdminDeployProperties deploy = new AdminDeployProperties();
        deploy.setDeployMode("standalone");

        AdminProductionGuard guard = new AdminProductionGuard(env, deploy);
        assertThatThrownBy(guard::validateProductionConfig)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void validateProductionConfig_clusterWithoutRedis_fails() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("zestflow.admin.registry-token", "prod-registry-secret");
        env.setProperty("zestflow.jwt.secret", "prod-jwt-secret-value");
        env.setProperty("zestflow.admin.deploy-mode", "cluster");

        AdminDeployProperties deploy = new AdminDeployProperties();
        deploy.setDeployMode("cluster");

        AdminProductionGuard guard = new AdminProductionGuard(env, deploy);
        assertThatThrownBy(guard::validateProductionConfig)
                .isInstanceOf(IllegalStateException.class);
    }
}
