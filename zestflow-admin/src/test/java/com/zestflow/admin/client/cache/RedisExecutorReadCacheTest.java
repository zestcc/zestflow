package com.zestflow.admin.client.cache;

import com.zestflow.admin.config.AdminRedisConditions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RedisExecutorReadCacheTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOps;
    @Mock
    private SetOperations<String, String> setOps;

    private RedisExecutorReadCache cache;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        ExecutorReadCacheProperties props = new ExecutorReadCacheProperties();
        props.setTtlMinutes(30);
        cache = new RedisExecutorReadCache(redisTemplate, props);
    }

    @Test
    void put_writesValueAndIndexesByApp() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        String key = ExecutorReadCache.buildKey("demo-app", "/api/chains", "?page=1");

        cache.put(key, "{\"records\":[]}");

        verify(valueOps).set(eq("zestflow:admin:executor-read:" + key), anyString(), eq(30L * 60), eq(TimeUnit.SECONDS));
        verify(setOps).add("zestflow:admin:executor-read:app:demo-app", "zestflow:admin:executor-read:" + key);
    }

    @Test
    void get_deserializesStoredEntry() {
        String key = ExecutorReadCache.buildKey("demo-app", "/api/components", "?page=1");
        when(valueOps.get("zestflow:admin:executor-read:" + key))
                .thenReturn("{\"json\":\"{\\\"total\\\":1}\",\"cachedAtMs\":1700000000000}");

        Optional<ExecutorReadCache.Entry> entry = cache.get(key);

        assertThat(entry).isPresent();
        assertThat(entry.get().json()).contains("\"total\":1");
        assertThat(entry.get().cachedAtMs()).isEqualTo(1700000000000L);
    }

    @Test
    void invalidateApp_deletesIndexedKeys() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.members("zestflow:admin:executor-read:app:demo-app"))
                .thenReturn(Set.of("zestflow:admin:executor-read:demo-app|/api/chains|"));

        cache.invalidateApp("demo-app");

        ArgumentCaptor<Set<String>> keysCaptor = ArgumentCaptor.forClass(Set.class);
        verify(redisTemplate).delete(keysCaptor.capture());
        assertThat(keysCaptor.getValue()).contains("zestflow:admin:executor-read:demo-app|/api/chains|");
        verify(redisTemplate).delete("zestflow:admin:executor-read:app:demo-app");
    }
}
