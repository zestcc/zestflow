package com.zestflow.admin.registry;

import com.zestflow.common.constant.RegistryConstants;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryRegistryLiveStoreTest {

    @Test
    void touchAndAliveWithinDeadTimeout() {
        InMemoryRegistryLiveStore store = new InMemoryRegistryLiveStore();
        store.touchExecutor("exec-1");
        assertThat(store.isExecutorAlive("exec-1")).isTrue();
        assertThat(store.aliveExecutorIds()).contains("exec-1");
    }

    @Test
    void removeClearsAliveState() {
        InMemoryRegistryLiveStore store = new InMemoryRegistryLiveStore();
        store.touchCollector("collector-1");
        store.removeCollector("collector-1");
        assertThat(store.isCollectorAlive("collector-1")).isFalse();
    }

    @Test
    void seedDoesNotOverwriteExistingTouch() {
        InMemoryRegistryLiveStore store = new InMemoryRegistryLiveStore();
        store.touchExecutor("exec-1");
        long seen = store.executorLastSeenEpochMs("exec-1").orElseThrow();
        store.seedExecutor("exec-1", seen - RegistryConstants.deadTimeoutMillis());
        assertThat(store.executorLastSeenEpochMs("exec-1").orElseThrow()).isEqualTo(seen);
    }
}
