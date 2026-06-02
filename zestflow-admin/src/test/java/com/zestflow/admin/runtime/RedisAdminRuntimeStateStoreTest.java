package com.zestflow.admin.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zestflow.common.model.dto.ChainSyncDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisAdminRuntimeStateStoreTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOps;
    @Mock
    private SetOperations<String, String> setOps;

    private RedisAdminRuntimeStateStore store;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        AdminRuntimeStateProperties props = new AdminRuntimeStateProperties();
        props.setTtlSeconds(120);
        store = new RedisAdminRuntimeStateStore(redisTemplate, new ObjectMapper(), props);
    }

    @Test
    void savePublishProgress_writesCommaSeparatedValue() {
        store.savePublishProgress("chain-a", 2, 3);

        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(eq("zestflow:admin:publish:chain-a"), valueCaptor.capture(), any(Duration.class));
        assertThat(valueCaptor.getValue()).isEqualTo("2,3");
    }

    @Test
    void getPublishProgress_parsesStoredValue() {
        when(valueOps.get("zestflow:admin:publish:chain-a")).thenReturn("2,3");

        Optional<int[]> progress = store.getPublishProgress("chain-a");

        assertThat(progress).isPresent();
        assertThat(progress.get()).containsExactly(2, 3);
    }

    @Test
    void saveChainSync_indexesExecutorId() throws Exception {
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        ChainSyncDTO sync = ChainSyncDTO.builder()
                .executorId("exec-1")
                .status("READY")
                .loadedChains(java.util.List.of("c1"))
                .timestamp(System.currentTimeMillis())
                .build();

        store.saveChainSync(sync);

        verify(setOps).add("zestflow:admin:chain-sync:index", "exec-1");
        verify(valueOps).set(eq("zestflow:admin:chain-sync:exec-1"), anyString(), any(Duration.class));
    }

    @Test
    void evictStaleChainSync_removesExpiredEntries() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.members("zestflow:admin:chain-sync:index")).thenReturn(Set.of("exec-old"));
        when(valueOps.get("zestflow:admin:chain-sync:exec-old")).thenReturn(
                "{\"executorId\":\"exec-old\",\"status\":\"READY\",\"timestamp\":1000}");

        store.evictStaleChainSync(System.currentTimeMillis());

        verify(redisTemplate).delete("zestflow:admin:chain-sync:exec-old");
        verify(setOps).remove("zestflow:admin:chain-sync:index", "exec-old");
    }
}
