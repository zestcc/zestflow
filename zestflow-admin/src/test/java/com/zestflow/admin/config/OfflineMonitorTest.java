package com.zestflow.admin.config;

import com.zestflow.admin.model.entity.CollectorRegistryPO;
import com.zestflow.admin.model.entity.ExecutorRegistryPO;
import com.zestflow.admin.registry.InMemoryRegistryLiveStore;
import com.zestflow.admin.registry.RegistryLiveStore;
import com.zestflow.admin.repository.CollectorRegistryMapper;
import com.zestflow.admin.repository.ExecutorRegistryMapper;
import com.zestflow.common.constant.RegistryConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OfflineMonitorTest {

    @Mock
    private ExecutorRegistryMapper executorRegistryMapper;

    @Mock
    private CollectorRegistryMapper collectorRegistryMapper;

    private RegistryLiveStore liveStore;
    private OfflineMonitorService service;

    @BeforeEach
    void setUp() {
        liveStore = new InMemoryRegistryLiveStore();
        service = new OfflineMonitorService(executorRegistryMapper, collectorRegistryMapper, liveStore);
    }

    @Test
    void checkOffline_marksStaleOnlineAsAbnormal() {
        ExecutorRegistryPO online = executor("exec-1", RegistryConstants.STATUS_ONLINE);
        when(executorRegistryMapper.selectList(any())).thenReturn(List.of(online));
        when(collectorRegistryMapper.selectList(any())).thenReturn(List.of());

        service.checkOffline();

        ArgumentCaptor<ExecutorRegistryPO> captor = ArgumentCaptor.forClass(ExecutorRegistryPO.class);
        verify(executorRegistryMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(RegistryConstants.STATUS_ABNORMAL);
    }

    @Test
    void checkOffline_aliveHeartbeat_keepsOnline() {
        liveStore.touchExecutor("exec-1");
        ExecutorRegistryPO online = executor("exec-1", RegistryConstants.STATUS_ONLINE);
        when(executorRegistryMapper.selectList(any())).thenReturn(List.of(online));
        when(collectorRegistryMapper.selectList(any())).thenReturn(List.of());

        service.checkOffline();

        verify(executorRegistryMapper, never()).updateById(any(ExecutorRegistryPO.class));
    }

    @Test
    void checkOffline_recoversAbnormalWhenHeartbeatReturns() {
        liveStore.touchExecutor("exec-1");
        ExecutorRegistryPO abnormal = executor("exec-1", RegistryConstants.STATUS_ABNORMAL);
        when(executorRegistryMapper.selectList(any())).thenReturn(List.of(abnormal));
        when(collectorRegistryMapper.selectList(any())).thenReturn(List.of());

        service.checkOffline();

        ArgumentCaptor<ExecutorRegistryPO> captor = ArgumentCaptor.forClass(ExecutorRegistryPO.class);
        verify(executorRegistryMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(RegistryConstants.STATUS_ONLINE);
    }

    private static ExecutorRegistryPO executor(String id, int status) {
        ExecutorRegistryPO po = new ExecutorRegistryPO();
        po.setExecutorId(id);
        po.setStatus(status);
        return po;
    }
}
