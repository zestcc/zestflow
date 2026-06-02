package com.zestflow.executor.engine;

import com.zestflow.common.constant.ChainConstants;
import com.zestflow.executor.chain.ChainDefinition;
import com.zestflow.executor.lifecycle.ChainStateMachine;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ChainInstanceManagerTest {

    @Test
    void awaitIdleReturnsWhenNoRunningInstances() throws InterruptedException {
        ChainInstanceManager manager = new ChainInstanceManager();
        assertThat(manager.awaitIdle(500)).isTrue();
    }

    @Test
    void awaitIdleWaitsUntilInstanceCompletes() throws Exception {
        ChainInstanceManager manager = new ChainInstanceManager();
        ChainDefinition definition = ChainDefinition.builder()
                .code("c1")
                .nodes(Map.of())
                .build();
        ChainInstance instance = new ChainInstance(definition, Map.of());
        manager.register(instance);

        ChainStateMachine sm = instance.getStateMachine();
        sm.transit(ChainConstants.CHAIN_LOADING);
        sm.transit(ChainConstants.CHAIN_READY);
        sm.transit(ChainConstants.CHAIN_RUNNING);

        Thread completer = new Thread(() -> {
            sleepQuietly(150);
            sm.transit(ChainConstants.CHAIN_SUCCESS);
        });
        completer.start();

        assertThat(manager.awaitIdle(2_000)).isTrue();
        completer.join();
        assertThat(manager.countRunning()).isZero();
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
