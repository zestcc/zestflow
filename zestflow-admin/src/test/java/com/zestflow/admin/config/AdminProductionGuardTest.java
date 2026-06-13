package com.zestflow.admin.config;

import com.zestflow.admin.runtime.AdminDeployProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminProductionGuardTest {

    private MockEnvironment validProdEnv() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("zestflow.admin.registry-token", "prod-registry-secret-ok");
        env.setProperty("zestflow.admin.executor-access-token", "prod-executor-access-token");
        env.setProperty("zestflow.collector.access-token", "prod-collector-access-token");
        env.setProperty("zestflow.jwt.secret", "prod-jwt-secret-value-at-least-32-characters-long");
        env.setProperty("zestflow.admin.default-user.password", "Str0ng-P@ssw0rd-2026");
        env.setProperty("zestflow.playground.enabled", "false");
        env.setProperty("zestflow.tenant.ip-demo-mode", "disabled");
        env.setProperty("spring.flyway.enabled", "true");
        env.setProperty("spring.flyway.validate-on-migrate", "true");
        env.setProperty("spring.flyway.out-of-order", "false");
        env.setProperty("springdoc.swagger-ui.enabled", "false");
        env.setProperty("springdoc.api-docs.enabled", "false");
        return env;
    }

    @Test
    void validateProductionConfig_standaloneWithSecrets_passes() {
        AdminDeployProperties deploy = new AdminDeployProperties();
        deploy.setDeployMode("standalone");

        AdminProductionGuard guard = new AdminProductionGuard(validProdEnv(), deploy);
        assertThatCode(guard::validateProductionConfig).doesNotThrowAnyException();
    }

    @Test
    void validateProductionConfig_missingRegistryToken_fails() {
        MockEnvironment env = validProdEnv();
        env.setProperty("zestflow.admin.registry-token", "");

        AdminDeployProperties deploy = new AdminDeployProperties();
        deploy.setDeployMode("standalone");

        AdminProductionGuard guard = new AdminProductionGuard(env, deploy);
        assertThatThrownBy(guard::validateProductionConfig)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("生产环境配置不完整");
    }

    @Test
    void validateProductionConfig_defaultJwtSecret_fails() {
        MockEnvironment env = validProdEnv();
        env.setProperty("zestflow.jwt.secret", "ZestFlow_dev_JWT_Secret_Key_Change_Me_In_Production_!!_");

        AdminDeployProperties deploy = new AdminDeployProperties();
        deploy.setDeployMode("standalone");

        AdminProductionGuard guard = new AdminProductionGuard(env, deploy);
        assertThatThrownBy(guard::validateProductionConfig)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void validateProductionConfig_playgroundEnabled_fails() {
        MockEnvironment env = validProdEnv();
        env.setProperty("zestflow.playground.enabled", "true");

        AdminDeployProperties deploy = new AdminDeployProperties();
        deploy.setDeployMode("standalone");

        AdminProductionGuard guard = new AdminProductionGuard(env, deploy);
        assertThatThrownBy(guard::validateProductionConfig)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void validateProductionConfig_defaultAdminPassword_fails() {
        MockEnvironment env = validProdEnv();
        env.setProperty("zestflow.admin.default-user.password", "admin123");

        AdminDeployProperties deploy = new AdminDeployProperties();
        deploy.setDeployMode("standalone");

        AdminProductionGuard guard = new AdminProductionGuard(env, deploy);
        assertThatThrownBy(guard::validateProductionConfig)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void validateProductionConfig_flywayDisabled_fails() {
        MockEnvironment env = validProdEnv();
        env.setProperty("spring.flyway.enabled", "false");

        AdminDeployProperties deploy = new AdminDeployProperties();
        deploy.setDeployMode("standalone");

        AdminProductionGuard guard = new AdminProductionGuard(env, deploy);
        assertThatThrownBy(guard::validateProductionConfig)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void validateProductionConfig_flywayOutOfOrder_fails() {
        MockEnvironment env = validProdEnv();
        env.setProperty("spring.flyway.out-of-order", "true");

        AdminDeployProperties deploy = new AdminDeployProperties();
        deploy.setDeployMode("standalone");

        AdminProductionGuard guard = new AdminProductionGuard(env, deploy);
        assertThatThrownBy(guard::validateProductionConfig)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void validateProductionConfig_flywayValidateDisabled_fails() {
        MockEnvironment env = validProdEnv();
        env.setProperty("spring.flyway.validate-on-migrate", "false");

        AdminDeployProperties deploy = new AdminDeployProperties();
        deploy.setDeployMode("standalone");

        AdminProductionGuard guard = new AdminProductionGuard(env, deploy);
        assertThatThrownBy(guard::validateProductionConfig)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void validateProductionConfig_springdocApiDocsEnabled_fails() {
        MockEnvironment env = validProdEnv();
        env.setProperty("springdoc.api-docs.enabled", "true");

        AdminDeployProperties deploy = new AdminDeployProperties();
        deploy.setDeployMode("standalone");

        AdminProductionGuard guard = new AdminProductionGuard(env, deploy);
        assertThatThrownBy(guard::validateProductionConfig)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void validateProductionConfig_clusterWithoutRedis_fails() {
        MockEnvironment env = validProdEnv();
        env.setProperty("zestflow.admin.deploy-mode", "cluster");

        AdminDeployProperties deploy = new AdminDeployProperties();
        deploy.setDeployMode("cluster");

        AdminProductionGuard guard = new AdminProductionGuard(env, deploy);
        assertThatThrownBy(guard::validateProductionConfig)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void validateProductionConfig_ssoEnabledWithPlaceholderSecret_fails() {
        MockEnvironment env = validProdEnv();
        env.setProperty("zestflow.sso.enabled", "true");
        env.setProperty("zestflow.sso.client-id", "zestflow-admin");
        env.setProperty("zestflow.sso.client-secret", "change-me-sso-client-secret");
        env.setProperty("zestflow.sso.redirect-uri", "https://admin.example.com/login/callback");

        AdminDeployProperties deploy = new AdminDeployProperties();
        deploy.setDeployMode("standalone");

        AdminProductionGuard guard = new AdminProductionGuard(env, deploy);
        assertThatThrownBy(guard::validateProductionConfig)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void validateProductionConfig_ssoEnabledWithValidSecret_passes() {
        MockEnvironment env = validProdEnv();
        env.setProperty("zestflow.sso.enabled", "true");
        env.setProperty("zestflow.sso.client-id", "zestflow-admin");
        env.setProperty("zestflow.sso.client-secret", "prod-sso-client-secret-value");
        env.setProperty("zestflow.sso.redirect-uri", "https://admin.example.com/login/callback");

        AdminDeployProperties deploy = new AdminDeployProperties();
        deploy.setDeployMode("standalone");

        AdminProductionGuard guard = new AdminProductionGuard(env, deploy);
        assertThatCode(guard::validateProductionConfig).doesNotThrowAnyException();
    }
}
