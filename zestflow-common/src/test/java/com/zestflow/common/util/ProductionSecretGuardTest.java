package com.zestflow.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionSecretGuardTest {

    @Test
    void weakMachineToken_detectsPlaceholderAndShort() {
        assertTrue(ProductionSecretGuard.isWeakMachineToken(null));
        assertTrue(ProductionSecretGuard.isWeakMachineToken("your-registry-token"));
        assertTrue(ProductionSecretGuard.isWeakMachineToken("short-token"));
        assertFalse(ProductionSecretGuard.isWeakMachineToken("prod-registry-secret-ok"));
    }

    @Test
    void weakAdminPassword_rejectsDefault() {
        assertTrue(ProductionSecretGuard.isWeakAdminPassword("admin123"));
        assertTrue(ProductionSecretGuard.isWeakAdminPassword("your-strong-password"));
        assertFalse(ProductionSecretGuard.isWeakAdminPassword("Str0ng-P@ssw0rd-2026"));
    }

    @Test
    void defaultJwtSecret_rejectsDevMarker() {
        assertTrue(ProductionSecretGuard.isDefaultJwtSecret(
                "ZestFlow_dev_JWT_Secret_Key_Change_Me_In_Production_!!_"));
        assertFalse(ProductionSecretGuard.isDefaultJwtSecret(
                "prod-jwt-secret-value-at-least-32-chars-long"));
    }

    @Test
    void weakOAuthClientSecret_rejectsPlaceholder() {
        assertTrue(ProductionSecretGuard.isWeakOAuthClientSecret(null));
        assertTrue(ProductionSecretGuard.isWeakOAuthClientSecret("change-me-in-production"));
        assertFalse(ProductionSecretGuard.isWeakOAuthClientSecret("prod-sso-client-secret-value"));
    }
}
