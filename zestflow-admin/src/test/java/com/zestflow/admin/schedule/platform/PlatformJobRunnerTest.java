package com.zestflow.admin.schedule.platform;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zestflow.admin.model.entity.ScheduleLogPO;
import com.zestflow.admin.model.entity.SchedulePO;
import com.zestflow.admin.repository.ScheduleLogMapper;
import com.zestflow.admin.repository.ScheduleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlatformJobRunnerTest {

    @Mock
    private ScheduleMapper scheduleMapper;
    @Mock
    private ScheduleLogMapper scheduleLogMapper;
    @Mock
    private PlatformJobHandlerRegistry handlerRegistry;

    @InjectMocks
    private PlatformJobRunner platformJobRunner;

    private SchedulePO job;

    @BeforeEach
    void setUp() {
        job = new SchedulePO();
        job.setId(10L);
        job.setJobKey(PlatformJobKeys.OFFLINE_CLEANUP);
        job.setChainName("异常记录清理");
        job.setStatus(1);
        job.setRemote(0);
        job.setTenantId(1L);
        when(scheduleMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(job);
    }

    @Test
    void skipsWhenJobDisabled() {
        job.setStatus(0);

        platformJobRunner.runScheduledByKey(PlatformJobKeys.OFFLINE_CLEANUP);

        verify(scheduleLogMapper, never()).insert(any(ScheduleLogPO.class));
    }

    @Test
    void writesSuccessLog() throws Exception {
        when(handlerRegistry.get(PlatformJobKeys.OFFLINE_CLEANUP)).thenReturn(() -> "ok");

        platformJobRunner.runScheduledByKey(PlatformJobKeys.OFFLINE_CLEANUP);

        ArgumentCaptor<ScheduleLogPO> captor = ArgumentCaptor.forClass(ScheduleLogPO.class);
        verify(scheduleLogMapper).insert(captor.capture());
        verify(scheduleLogMapper).updateById(any(ScheduleLogPO.class));
        assertThat(captor.getValue().getJobKey()).isEqualTo(PlatformJobKeys.OFFLINE_CLEANUP);
        assertThat(captor.getValue().getTriggerType()).isEqualTo("cron");
    }
}
