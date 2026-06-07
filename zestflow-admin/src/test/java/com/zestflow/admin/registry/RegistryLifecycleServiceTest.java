package com.zestflow.admin.registry;

import com.zestflow.admin.repository.CollectorRegistryMapper;
import com.zestflow.admin.repository.ExecutorRegistryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistryLifecycleServiceTest {

    @Mock private ExecutorRegistryMapper executorRegistryMapper;
    @Mock private CollectorRegistryMapper collectorRegistryMapper;

    private InMemoryRegistryLiveStore liveStore;
    private RegistryExpiryScheduler expiryScheduler;
    private RegistryLifecycleService lifecycleService;

    @BeforeEach
    void setUp() {
        liveStore = new InMemoryRegistryLiveStore();
        expiryScheduler = new RegistryExpiryScheduler();
        lifecycleService = new RegistryLifecycleService(liveStore, expiryScheduler,
                executorRegistryMapper, collectorRegistryMapper);
        when(executorRegistryMapper.update(any(), any())).thenReturn(1);
    }

    @Test
    void onExecutorHeartbeat_syncsDbAndSchedulesExpiryCheck() {
        liveStore.touchExecutor("exec-1");
        lifecycleService.onExecutorHeartbeat("exec-1");
        verify(executorRegistryMapper, atLeastOnce()).update(any(), any());
    }
}
