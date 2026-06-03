package com.zestflow.executor.config;

import com.zestflow.common.util.ProductionSecretGuard;
import com.zestflow.executor.registry.ExecutorProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExecutorProductionGuardTest {

    @Test
    void validateProductionConfig_withStrongTokens_passes() {
        ExecutorProperties props = new ExecutorProperties();
        props.setAccessToken("prod-executor-access-token-ok");
        props.setRegistryToken("prod-registry-token-ok");

        ExecutorProductionGuard guard = new ExecutorProductionGuard(props);
        assertThatCode(guard::validateProductionConfig).doesNotThrowAnyException();
    }

    @Test
    void validateProductionConfig_missingAccessToken_fails() {
        ExecutorProperties props = new ExecutorProperties();
        props.setRegistryToken("prod-registry-token-ok");

        ExecutorProductionGuard guard = new ExecutorProductionGuard(props);
        assertThatThrownBy(guard::validateProductionConfig)
                .isInstanceOf(IllegalStateException.class);
    }
}
