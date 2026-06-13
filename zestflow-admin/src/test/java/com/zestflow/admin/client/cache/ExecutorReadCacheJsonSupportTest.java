package com.zestflow.admin.client.cache;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutorReadCacheJsonSupportTest {

    @Test
    void attachReadCacheMeta_addsStaleFlag() {
        String json = ExecutorReadCacheJsonSupport.attachReadCacheMeta(
                "{\"records\":[],\"total\":0}", 1_700_000_000_000L);

        assertThat(json).contains("\"_readCache\"");
        assertThat(json).contains("\"stale\":true");
        assertThat(json).contains("\"cachedAt\":1700000000000");
    }

    @Test
    void shouldSkipCache_skips404Payload() {
        assertThat(ExecutorReadCacheJsonSupport.shouldSkipCache("{\"code\":404,\"message\":\"x\"}")).isTrue();
        assertThat(ExecutorReadCacheJsonSupport.shouldSkipCache("{\"records\":[],\"total\":0}")).isFalse();
    }
}
