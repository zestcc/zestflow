package com.zestflow.common.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionSecretGuardTest {

    @Test
    void weakMachineToken_detectsPlaceholderAndShort() {
        assertThat(ProductionSecretGuard.isWeakMachineToken(null)).isTrue();
        assertThat(ProductionSecretGuard.isWeakMachineToken("your-registry-token")).isTrue();
        assertThat(ProductionSecretGuard.isWeakMachineToken("short-token")).isTrue();
        assertThat(ProductionSecretGuard.isWeakMachineToken("prod-registry-secret-ok")).isFalse();
    }

    @Test
    void weakAdminPassword_rejectsDefault() {
        assertThat(ProductionSecretGuard.isWeakAdminPassword("admin123")).isTrue();
        assertThat(ProductionSecretGuard.isWeakAdminPassword("your-strong-password")).isTrue();
        assertThat(ProductionSecretGuard.isWeakAdminPassword("Str0ng-P@ssw0rd-2026")).isFalse();
    }

    @Test
    void defaultJwtSecret_rejectsDevMarker() {
        assertThat(ProductionSecretGuard.isDefaultJwtSecret("ZestFlow_dev_JWT_Secret_Key_Change_Me_In_Production_!!_"))
                .isTrue();
        assertThat(ProductionSecretGuard.isDefaultJwtSecret("prod-jwt-secret-value-at-least-32-chars-long"))
                .isFalse();
    }
}
