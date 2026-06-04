package com.zestflow.common.protocol;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChainTransactionConfigTest {

    @Test
    void fromExtraConfigDisabledWhenMissing() {
        assertFalse(ChainTransactionConfig.fromExtraConfig(null).isEnabled());
        assertFalse(ChainTransactionConfig.fromExtraConfig(Map.of()).isEnabled());
    }

    @Test
    void fromExtraConfigParsesEnabledTransaction() {
        ChainTransactionConfig cfg = ChainTransactionConfig.fromExtraConfig(Map.of(
                "transaction", Map.of(
                        "enabled", true,
                        "propagation", "REQUIRES_NEW"
                )
        ));
        assertTrue(cfg.isEnabled());
        assertEquals("REQUIRES_NEW", cfg.getPropagation());
    }

    @Test
    void resolveNodePropagationUsesNodeOverride() {
        ChainTransactionConfig chain = ChainTransactionConfig.builder()
                .enabled(true)
                .propagation("REQUIRED")
                .build();
        assertEquals("REQUIRES_NEW", ChainTransactionConfig.resolveNodePropagation("REQUIRES_NEW", chain));
        assertEquals("REQUIRED", ChainTransactionConfig.resolveNodePropagation("INHERIT", chain));
    }

    @Test
    void requiresDedicatedTemplateWhenNodeOverrideSet() {
        ChainTransactionConfig chain = ChainTransactionConfig.disabled();
        assertTrue(ChainTransactionConfig.requiresDedicatedTemplate("REQUIRES_NEW", chain));
        assertFalse(ChainTransactionConfig.requiresDedicatedTemplate("INHERIT", chain));
        assertFalse(ChainTransactionConfig.requiresDedicatedTemplate(null, chain));
    }
}
