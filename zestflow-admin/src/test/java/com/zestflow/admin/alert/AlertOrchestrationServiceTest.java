package com.zestflow.admin.alert;

import com.zestflow.admin.registry.RegistryLiveStore;
import com.zestflow.admin.repository.ExecutorRegistryMapper;
import com.zestflow.admin.repository.ScheduleLogMapper;
import com.zestflow.admin.schedule.ScheduleChainProxyService;
import com.zestflow.admin.service.MailService;
import com.zestflow.admin.alert.AlertRule;
import com.zestflow.admin.repository.UserAppRoleMapper;
import com.zestflow.common.protocol.EventStats;
import com.zestflow.common.protocol.SlaAlertMetricsReportDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AlertOrchestrationServiceTest {

    @Mock private AlertConfigService alertConfigService;
    @Mock private UserAppRoleMapper userAppRoleMapper;
    @Mock private AlertRecipientService recipientService;
    @Mock private AlertCooldownService cooldownService;
    @Mock private AlertHistoryService alertHistoryService;
    @Mock private ExecutorRegistryMapper executorRegistryMapper;
    @Mock private RegistryLiveStore liveStore;
    @Mock private ScheduleLogMapper scheduleLogMapper;
    @Mock private ScheduleChainProxyService scheduleChainProxyService;
    @Mock private MailService mailService;

    @InjectMocks
    private AlertOrchestrationService service;

    private EffectiveAlertConfig defaultConfig;

    @BeforeEach
    void setUp() {
        defaultConfig = EffectiveAlertConfig.builder()
                .enabled(true)
                .cooldownMinutes(30)
                .windowMinutes(60)
                .minExecutions(5)
                .successRateThreshold(95.0)
                .failCountThreshold(10)
                .p95CostMsThreshold(5000L)
                .scheduleFailThreshold(3)
                .alertNoOnlineExecutor(true)
                .subjectPrefix("[ZestFlow 告警]")
                .build();
        when(alertConfigService.resolveEffective(1L)).thenReturn(defaultConfig);
        when(recipientService.resolveRecipientEmails(1L, "demo")).thenReturn(List.of("ops@example.com"));
        when(cooldownService.shouldSend(anyString(), anyInt())).thenReturn(true);
        when(scheduleLogMapper.selectCount(any())).thenReturn(0L);
        when(scheduleChainProxyService.countFailures(eq("demo"), any())).thenReturn(0L);
        when(executorRegistryMapper.selectCount(any())).thenReturn(0L);
    }

    @AfterEach
    void tearDown() {
        com.zestflow.admin.config.TenantContextHolder.clear();
    }

    @Test
    void processMetricsReport_sendsAlertWhenSuccessRateLow() {
        EventStats stats = EventStats.builder()
                .executionCount(20)
                .successRate(80.0)
                .failCount(4)
                .p95CostMs(100L)
                .build();
        SlaAlertMetricsReportDTO report = SlaAlertMetricsReportDTO.builder()
                .tenantId(1L)
                .appCode("demo")
                .eventStats(stats)
                .build();

        int[] sent = service.processMetricsReport(report);

        assertThat(sent[0]).isEqualTo(1);
        verify(mailService).sendSlaAlertEmail(anyList(), any());
        verify(alertHistoryService).record(eq(1L), eq("demo"), eq(AlertRule.LOW_SUCCESS_RATE), any(), anyList());
    }
}
