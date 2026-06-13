package com.zestflow.admin.service.sso.revocation;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemorySsoSessionRevocationStoreTest {

    @Test
    void revokeAndCheck() {
        InMemorySsoSessionRevocationStore store = new InMemorySsoSessionRevocationStore(Duration.ofHours(1));
        store.revokeByUsername("admin");
        assertTrue(store.isRevoked("admin"));
    }

    @Test
    void notRevokedByDefault() {
        InMemorySsoSessionRevocationStore store = new InMemorySsoSessionRevocationStore(Duration.ofHours(1));
        assertFalse(store.isRevoked("user1"));
    }

    @Test
    void clearRevocation() {
        InMemorySsoSessionRevocationStore store = new InMemorySsoSessionRevocationStore(Duration.ofHours(1));
        store.revokeByUsername("admin");
        store.clearRevocation("admin");
        assertFalse(store.isRevoked("admin"));
    }

    @Test
    void ignoresBlankUsername() {
        InMemorySsoSessionRevocationStore store = new InMemorySsoSessionRevocationStore(Duration.ofHours(1));
        store.revokeByUsername("  ");
        assertFalse(store.isRevoked(null));
    }
}
