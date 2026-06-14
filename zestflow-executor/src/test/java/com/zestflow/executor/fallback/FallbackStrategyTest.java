package com.zestflow.executor.fallback;

import com.zestflow.executor.chain.NodeDefinition;
import com.zestflow.executor.context.ChainContext;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FallbackStrategyTest {

    private final DefaultFallbackStrategy strategy = new DefaultFallbackStrategy();

    @Test
    void defaultMode_logsAndReturnsNull() {
        NodeDefinition nodeDef = NodeDefinition.builder().id("n1").component("c1").fallbackMode("default").build();
        ChainContext ctx = new ChainContext("i1", "c1", Map.of());

        assertThat(strategy.fallback(nodeDef, ctx, new RuntimeException("boom"))).isNull();
    }

    @Test
    void constantMode_writesContext() {
        NodeDefinition nodeDef = NodeDefinition.builder()
                .id("n1")
                .fallbackMode("constant")
                .fallbackConstant("{\"ok\":true}")
                .build();
        ChainContext ctx = new ChainContext("i1", "c1", Map.of());

        Object result = strategy.fallback(nodeDef, ctx, new RuntimeException("boom"));

        assertThat(result).isEqualTo(Map.of("ok", true));
        assertThat(ctx.get("n1")).isEqualTo(Map.of("ok", true));
    }

    @Test
    void propagateMode_rethrowsRuntime() {
        NodeDefinition nodeDef = NodeDefinition.builder().id("n1").fallbackMode("propagate").build();
        ChainContext ctx = new ChainContext("i1", "c1", Map.of());
        RuntimeException cause = new RuntimeException("original");

        assertThatThrownBy(() -> strategy.fallback(nodeDef, ctx, cause))
                .isSameAs(cause);
    }

    @Test
    void parseConstant_supportsPlainString() {
        assertThat(DefaultFallbackStrategy.parseConstant("fallback-value")).isEqualTo("fallback-value");
        assertThat(DefaultFallbackStrategy.parseConstant("42")).isEqualTo(42);
    }
}
