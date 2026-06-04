package com.zestflow.executor.context;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ExecuteResultPublisherTest {

    record SampleResult(double cashbackAmount, String rule) {
    }

    @Test
    void publishMap_mergesStringKeys() {
        ChainContext ctx = new ChainContext("i1", "c1", Map.of());
        ExecuteResultPublisher.publish(ctx, Map.of("a", 1, "b", "two"));
        assertThat(ctx.get("a")).isEqualTo(1);
        assertThat(ctx.get("b")).isEqualTo("two");
    }

    @Test
    void publishSimpleValueWithOutputKey() {
        ChainContext ctx = new ChainContext("i1", "c1", Map.of());
        ExecuteResultPublisher.publish(ctx, "rc", "supplierType");
        assertThat(ctx.get("supplierType")).isEqualTo("rc");
    }

    @Test
    void publishSimpleValueWithoutOutputKeyIsIgnored() {
        ChainContext ctx = new ChainContext("i1", "c1", Map.of());
        ExecuteResultPublisher.publish(ctx, "rc");
        assertThat(ctx.get("supplierType")).isNull();
    }

    @Test
    void publishPojo_registersTypedAndFlattensProperties() {
        ChainContext ctx = new ChainContext("i1", "c1", Map.of());
        SampleResult result = new SampleResult(12.5, "RULE_X");
        ExecuteResultPublisher.publish(ctx, result);

        assertThat(ctx.getTyped(SampleResult.class)).isEqualTo(result);
        assertThat(ctx.get("cashbackAmount")).isEqualTo(12.5);
        assertThat(ctx.get("rule")).isEqualTo("RULE_X");
    }
}
