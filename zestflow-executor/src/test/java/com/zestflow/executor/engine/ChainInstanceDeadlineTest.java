package com.zestflow.executor.engine;

import com.zestflow.common.constant.ChainConstants;
import com.zestflow.executor.chain.ChainDefinition;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ChainInstanceDeadlineTest {

    @Test
    void resolveDeadlineUsesChainTimeoutWhenNoParent() {
        long start = System.currentTimeMillis();
        ChainDefinition def = ChainDefinition.builder()
                .code("c1")
                .timeout(10_000L)
                .nodes(Map.of())
                .build();

        long deadline = ChainInstance.resolveDeadline(def, ChainInstance.NO_PARENT_DEADLINE, start);

        assertThat(deadline).isEqualTo(start + 10_000L);
    }

    @Test
    void resolveDeadlineUsesMinOfParentAndChain() {
        long start = System.currentTimeMillis();
        long parentDeadline = start + 3_000L;
        ChainDefinition def = ChainDefinition.builder()
                .code("c1")
                .timeout(10_000L)
                .nodes(Map.of())
                .build();

        long deadline = ChainInstance.resolveDeadline(def, parentDeadline, start);

        assertThat(deadline).isEqualTo(parentDeadline);
    }

    @Test
    void resolveDeadlineUsesParentWhenChainUnlimited() {
        long start = System.currentTimeMillis();
        long parentDeadline = start + 5_000L;
        ChainDefinition def = ChainDefinition.builder()
                .code("c1")
                .timeout(ChainConstants.DEFAULT_CHAIN_TIMEOUT_MS)
                .nodes(Map.of())
                .build();
        ChainDefinition unlimited = ChainDefinition.builder()
                .code("c2")
                .timeout(0)
                .nodes(Map.of())
                .build();

        assertThat(ChainInstance.resolveDeadline(unlimited, parentDeadline, start)).isEqualTo(parentDeadline);
    }

    @Test
    void metadataDeadlineIsSetOnContext() {
        ChainDefinition def = ChainDefinition.builder()
                .code("c1")
                .timeout(5_000L)
                .nodes(Map.of())
                .build();
        ChainInstance instance = new ChainInstance(def, Map.of());

        assertThat(instance.getContext().getMetadata(ChainConstants.META_DEADLINE_MS))
                .isEqualTo(instance.getDeadlineMs());
        assertThat(instance.hasDeadline()).isTrue();
    }
}
