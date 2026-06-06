package com.zestflow.admin.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zestflow.admin.model.entity.ScheduleLogPO;
import com.zestflow.admin.model.entity.SchedulePO;
import com.zestflow.admin.repository.ScheduleLogMapper;
import com.zestflow.admin.repository.ScheduleMapper;
import com.zestflow.admin.service.impl.ScheduleServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduleScanServiceTest {

    @Mock
    private ScheduleMapper scheduleMapper;
    @Mock
    private ScheduleLogMapper scheduleLogMapper;
    @Mock
    private ScheduleServiceImpl scheduleService;

    @InjectMocks
    private ScheduleScanService scheduleScanService;

    private SchedulePO dueSchedule;

    @BeforeEach
    void setUp() {
        dueSchedule = new SchedulePO();
        dueSchedule.setId(1L);
        dueSchedule.setStatus(1);
        dueSchedule.setChainCode("demo-chain");
        dueSchedule.setJobType("CHAIN");
        dueSchedule.setCron("* * * * * *");
    }

    @Test
    void skipsWhenNoEnabledSchedules() {
        when(scheduleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        scheduleScanService.scanAndTriggerDueSchedules();

        verify(scheduleService, never()).doTrigger(any(), any(), any());
    }

    @Test
    void triggersWhenCronIsDue() {
        when(scheduleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(dueSchedule));
        when(scheduleLogMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        scheduleScanService.scanAndTriggerDueSchedules();

        ArgumentCaptor<SchedulePO> scheduleCaptor = ArgumentCaptor.forClass(SchedulePO.class);
        verify(scheduleService).doTrigger(scheduleCaptor.capture(), org.mockito.ArgumentMatchers.eq("cron"), any());
        assertThat(scheduleCaptor.getValue().getId()).isEqualTo(1L);
    }

    @Test
    void doesNotTriggerWhenAlreadyFiredRecently() {
        ScheduleLogPO recentLog = new ScheduleLogPO();
        recentLog.setTriggeredAt(LocalDateTime.now());

        when(scheduleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(dueSchedule));
        when(scheduleLogMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(recentLog);

        scheduleScanService.scanAndTriggerDueSchedules();

        verify(scheduleService, never()).doTrigger(any(), any(), any());
    }
}
