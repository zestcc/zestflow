package com.zestflow.admin.tenant;

import com.zestflow.admin.model.entity.TenantPO;
import com.zestflow.admin.playground.repository.PlaygroundRecordMapper;
import com.zestflow.admin.playground.repository.PlaygroundSceneMapper;
import com.zestflow.admin.repository.CollectorRegistryMapper;
import com.zestflow.admin.repository.DictDataMapper;
import com.zestflow.admin.repository.DictTypeMapper;
import com.zestflow.admin.repository.ExecutorRegistryMapper;
import com.zestflow.admin.repository.RoleMapper;
import com.zestflow.admin.repository.ScheduleLogMapper;
import com.zestflow.admin.repository.ScheduleMapper;
import com.zestflow.admin.repository.TenantIpMappingMapper;
import com.zestflow.admin.repository.TenantMapper;
import com.zestflow.admin.repository.UserAppRoleMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantLifecycleServiceTest {

    @Mock private TenantMapper tenantMapper;
    @Mock private TenantIpMappingMapper tenantIpMappingMapper;
    @Mock private PlaygroundSceneMapper playgroundSceneMapper;
    @Mock private PlaygroundRecordMapper playgroundRecordMapper;
    @Mock private ScheduleMapper scheduleMapper;
    @Mock private ScheduleLogMapper scheduleLogMapper;
    @Mock private DictTypeMapper dictTypeMapper;
    @Mock private DictDataMapper dictDataMapper;
    @Mock private RoleMapper roleMapper;
    @Mock private UserAppRoleMapper userAppRoleMapper;
    @Mock private ExecutorRegistryMapper executorRegistryMapper;
    @Mock private CollectorRegistryMapper collectorRegistryMapper;

    @InjectMocks
    private TenantLifecycleService lifecycleService;

    @Test
    void purgeTrialTenant_skipsSystemTemplate() {
        assertThat(lifecycleService.purgeTrialTenant(1L)).isFalse();
        verify(tenantMapper, never()).deleteById(anyLong());
    }

    @Test
    void purgeTrialTenant_deletesTrialData() {
        TenantPO trial = new TenantPO();
        trial.setId(99L);
        trial.setTenantType(TenantTypes.TRIAL);
        trial.setCode("demo-abc");
        when(tenantMapper.selectById(99L)).thenReturn(trial);

        assertThat(lifecycleService.purgeTrialTenant(99L)).isTrue();

        verify(playgroundRecordMapper).delete(any());
        verify(playgroundSceneMapper).delete(any());
        verify(scheduleLogMapper).delete(any());
        verify(scheduleMapper).delete(any());
        verify(dictDataMapper).delete(any());
        verify(dictTypeMapper).delete(any());
        verify(userAppRoleMapper).delete(any());
        verify(roleMapper).delete(any());
        verify(executorRegistryMapper).delete(any());
        verify(collectorRegistryMapper).delete(any());
        verify(tenantIpMappingMapper).delete(any());
        verify(tenantMapper).deleteById(99L);
    }
}
