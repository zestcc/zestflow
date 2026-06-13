package com.zestflow.admin.client.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CaffeineExecutorReadCacheTest {

    private CaffeineExecutorReadCache cache;

    @BeforeEach
    void setUp() {
        ExecutorReadCacheProperties props = new ExecutorReadCacheProperties();
        props.setTtlMinutes(30);
        props.setMaxEntries(100);
        cache = new CaffeineExecutorReadCache(props);
    }

    @Test
    void putAndGet_returnsCachedJson() {
        String key = ExecutorReadCache.buildKey("demo-app", "/api/chains", "?page=1");
        cache.put(key, "{\"records\":[{\"code\":\"CHN001\"}],\"total\":1}");

        assertThat(cache.get(key)).isPresent();
        assertThat(cache.get(key).get().json()).contains("CHN001");
    }

    @Test
    void invalidateApp_removesAllKeysForApp() {
        String key1 = ExecutorReadCache.buildKey("demo-app", "/api/chains", "?page=1");
        String key2 = ExecutorReadCache.buildKey("demo-app", "/api/designs", "?page=1");
        String key3 = ExecutorReadCache.buildKey("other-app", "/api/chains", "?page=1");
        cache.put(key1, "a");
        cache.put(key2, "b");
        cache.put(key3, "c");

        cache.invalidateApp("demo-app");

        assertThat(cache.get(key1)).isEmpty();
        assertThat(cache.get(key2)).isEmpty();
        assertThat(cache.get(key3)).isPresent();
    }
}
