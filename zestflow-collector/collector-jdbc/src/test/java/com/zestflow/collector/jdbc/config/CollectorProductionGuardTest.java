package com.zestflow.collector.jdbc.config;

import com.zestflow.common.util.ProductionSecretGuard;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CollectorProductionGuardTest {

    @Test
    void validateProductionConfig_withStrongToken_passes() {
        CollectorProperties props = new CollectorProperties();
        props.setAccessToken("prod-collector-access-token-ok");

        CollectorProductionGuard guard = new CollectorProductionGuard(props);
        assertThatCode(guard::validateProductionConfig).doesNotThrowAnyException();
    }

    @Test
    void validateProductionConfig_placeholderToken_fails() {
        CollectorProperties props = new CollectorProperties();
        props.setAccessToken("your-collector-access-token");

        CollectorProductionGuard guard = new CollectorProductionGuard(props);
        assertThatThrownBy(guard::validateProductionConfig)
                .isInstanceOf(IllegalStateException.class);
    }
}
