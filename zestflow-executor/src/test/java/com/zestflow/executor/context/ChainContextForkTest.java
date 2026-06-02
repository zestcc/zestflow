package com.zestflow.executor.context;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ChainContextForkTest {

    @Test
    void forkCopiesDataBusWithoutSharingMutableMap() {
        ChainContext parent = new ChainContext("inst-1", "chain-1", Map.of("seed", 1));
        ChainContext fork = parent.fork();

        fork.put("branch", "B");
        parent.put("parentOnly", "P");

        assertThat(parent.get("branch")).isNull();
        assertThat(fork.get("parentOnly")).isNull();
        assertThat(fork.get("seed")).isEqualTo(1);
        assertThat(fork.getInstanceId()).isEqualTo(parent.getInstanceId());
        assertThat(fork.getChainCode()).isEqualTo(parent.getChainCode());
        assertThat(fork.getStartTime()).isEqualTo(parent.getStartTime());
    }

    @Test
    void mergeFromCombinesForkOutputs() {
        ChainContext parent = new ChainContext("inst-1", "chain-1", Map.of("shared", "keep"));
        ChainContext forkB = parent.fork();
        ChainContext forkC = parent.fork();

        forkB.put("resultB", "B");
        forkC.put("resultC", "C");

        parent.mergeFrom(forkB);
        parent.mergeFrom(forkC);

        assertThat(parent.get("shared")).isEqualTo("keep");
        assertThat(parent.get("resultB")).isEqualTo("B");
        assertThat(parent.get("resultC")).isEqualTo("C");
    }

    @Test
    void mergeFromMergesTypedData() {
        ChainContext parent = new ChainContext("inst-1", "chain-1", null);
        ChainContext fork = parent.fork();
        fork.register("typed-value");

        parent.mergeFrom(fork);

        assertThat(parent.getTyped(String.class)).isEqualTo("typed-value");
    }
}
