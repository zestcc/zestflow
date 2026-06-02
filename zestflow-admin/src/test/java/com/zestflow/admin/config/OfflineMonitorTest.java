package com.zestflow.admin.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zestflow.admin.model.entity.CollectorRegistryPO;
import com.zestflow.admin.model.entity.ExecutorRegistryPO;
import com.zestflow.admin.repository.CollectorRegistryMapper;
import com.zestflow.admin.repository.ExecutorRegistryMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OfflineMonitorTest {

    @Mock
    private ExecutorRegistryMapper executorRegistryMapper;

    @Mock
    private CollectorRegistryMapper collectorRegistryMapper;

    private OfflineMonitorService newService() {
        return new OfflineMonitorService(executorRegistryMapper, collectorRegistryMapper);
    }

    @Test
    void checkOffline_marksStaleOnlineAsAbnormal() {
        try (MockedStatic<Wrappers> wrappers = mockStatic(Wrappers.class)) {
            LambdaUpdateWrapper<ExecutorRegistryPO> mockWrapper = mock(LambdaUpdateWrapper.class);
            when(mockWrapper.set(any(), any())).thenReturn(mockWrapper);
            when(mockWrapper.eq(any(), any())).thenReturn(mockWrapper);
            when(mockWrapper.lt(any(), any())).thenReturn(mockWrapper);
            wrappers.when(Wrappers::lambdaUpdate).thenReturn(mockWrapper);
            when(executorRegistryMapper.update(any(), any())).thenReturn(2);
            when(collectorRegistryMapper.update(any(), any())).thenReturn(1);

            OfflineMonitorService service = newService();
            service.checkOffline();

            verify(executorRegistryMapper).update(any(), any());
        }
    }

    @Test
    void checkOffline_noStaleRecords_doesNothing() {
        try (MockedStatic<Wrappers> wrappers = mockStatic(Wrappers.class)) {
            LambdaUpdateWrapper<ExecutorRegistryPO> mockWrapper = mock(LambdaUpdateWrapper.class);
            when(mockWrapper.set(any(), any())).thenReturn(mockWrapper);
            when(mockWrapper.eq(any(), any())).thenReturn(mockWrapper);
            when(mockWrapper.lt(any(), any())).thenReturn(mockWrapper);
            wrappers.when(Wrappers::lambdaUpdate).thenReturn(mockWrapper);
            when(executorRegistryMapper.update(any(), any())).thenReturn(0);
            when(collectorRegistryMapper.update(any(), any())).thenReturn(0);

            OfflineMonitorService service = newService();
            service.checkOffline();

            verify(executorRegistryMapper).update(any(), any());
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    void cleanupStaleAbnormal_deletesOldAbnormalRecords() {
        try (MockedStatic<Wrappers> wrappers = mockStatic(Wrappers.class)) {
            LambdaQueryWrapper<ExecutorRegistryPO> mockWrapper = mock(LambdaQueryWrapper.class);
            when(mockWrapper.eq(any(), any())).thenReturn(mockWrapper);
            when(mockWrapper.lt(any(), any())).thenReturn(mockWrapper);
            wrappers.when(Wrappers::lambdaQuery).thenReturn(mockWrapper);
            when(executorRegistryMapper.delete(any())).thenReturn(3);
            when(collectorRegistryMapper.delete(any())).thenReturn(2);

            OfflineMonitorService service = newService();
            service.cleanupStaleAbnormal();

            verify(executorRegistryMapper).delete(any());
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    void cleanupStaleAbnormal_noStaleRecords_doesNothing() {
        try (MockedStatic<Wrappers> wrappers = mockStatic(Wrappers.class)) {
            LambdaQueryWrapper<ExecutorRegistryPO> mockWrapper = mock(LambdaQueryWrapper.class);
            when(mockWrapper.eq(any(), any())).thenReturn(mockWrapper);
            when(mockWrapper.lt(any(), any())).thenReturn(mockWrapper);
            wrappers.when(Wrappers::lambdaQuery).thenReturn(mockWrapper);
            when(executorRegistryMapper.delete(any())).thenReturn(0);
            when(collectorRegistryMapper.delete(any())).thenReturn(0);

            OfflineMonitorService service = newService();
            service.cleanupStaleAbnormal();

            verify(executorRegistryMapper).delete(any());
        }
    }

    @Test
    void noExceptionOnNormalExecution() {
        try (MockedStatic<Wrappers> wrappers = mockStatic(Wrappers.class)) {
            LambdaUpdateWrapper<ExecutorRegistryPO> mockUpdateWrapper = mock(LambdaUpdateWrapper.class);
            when(mockUpdateWrapper.set(any(), any())).thenReturn(mockUpdateWrapper);
            when(mockUpdateWrapper.eq(any(), any())).thenReturn(mockUpdateWrapper);
            when(mockUpdateWrapper.lt(any(), any())).thenReturn(mockUpdateWrapper);
            wrappers.when(Wrappers::lambdaUpdate).thenReturn(mockUpdateWrapper);

            LambdaQueryWrapper<ExecutorRegistryPO> mockQueryWrapper = mock(LambdaQueryWrapper.class);
            when(mockQueryWrapper.eq(any(), any())).thenReturn(mockQueryWrapper);
            when(mockQueryWrapper.lt(any(), any())).thenReturn(mockQueryWrapper);
            wrappers.when(Wrappers::lambdaQuery).thenReturn(mockQueryWrapper);

            when(executorRegistryMapper.update(any(), any())).thenReturn(0);
            when(executorRegistryMapper.delete(any())).thenReturn(0);
            when(collectorRegistryMapper.update(any(), any())).thenReturn(0);
            when(collectorRegistryMapper.delete(any())).thenReturn(0);

            OfflineMonitorService service = newService();

            assertThatCode(service::checkOffline).doesNotThrowAnyException();
            assertThatCode(service::cleanupStaleAbnormal).doesNotThrowAnyException();
        }
    }
}
