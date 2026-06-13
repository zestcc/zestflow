package com.zestflow.admin.service.sso.store;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InMemorySsoPkceStoreTest {

    @Test
    void saveAndConsume_returnsVerifierOnce() {
        InMemorySsoPkceStore store = new InMemorySsoPkceStore();
        store.save("state-1", "verifier-abc");

        assertThat(store.consume("state-1")).isEqualTo("verifier-abc");
        assertThat(store.consume("state-1")).isNull();
    }

    @Test
    void consume_unknownState_returnsNull() {
        InMemorySsoPkceStore store = new InMemorySsoPkceStore();
        assertThat(store.consume("missing")).isNull();
    }
}
