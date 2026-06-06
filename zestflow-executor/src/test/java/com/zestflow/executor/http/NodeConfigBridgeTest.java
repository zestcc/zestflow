package com.zestflow.executor.http;

import com.zestflow.executor.chain.NodeDefinition;
import com.zestflow.executor.context.ChainContext;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NodeConfigBridgeTest {

    @Test
    void mapsHttpConfigToContextKeys() {
        NodeDefinition node = NodeDefinition.builder()
                .id("h1")
                .type("HTTP_CLIENT")
                .config(Map.of(
                        "httpUrl", "https://api.example.com/users/${userId}",
                        "httpMethod", "POST",
                        "httpBodyTemplate", "{\"orderId\":\"${orderId}\"}"
                ))
                .build();
        ChainContext ctx = new ChainContext("i1", "c1", Map.of("userId", "U1", "orderId", "O1"));

        NodeConfigBridge.apply(node, ctx);

        assertThat(ctx.get("_http_url")).isEqualTo("https://api.example.com/users/U1");
        assertThat(ctx.get("_http_method")).isEqualTo("POST");
        assertThat(ctx.get("_http_body")).isEqualTo("{\"orderId\":\"O1\"}");
    }

    @Test
    void mapsCacheKeyTemplate() {
        NodeDefinition node = NodeDefinition.builder()
                .id("cr1")
                .type("CACHE_READER")
                .config(Map.of("cacheKey", "user:${userId}"))
                .build();
        ChainContext ctx = new ChainContext("i1", "c1", Map.of("userId", "U99"));

        NodeConfigBridge.apply(node, ctx);

        assertThat(ctx.get("cacheKey")).isEqualTo("user:U99");
    }
}
