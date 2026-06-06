package com.zestflow.admin.alert;

import com.zestflow.admin.client.CollectorQueryAggregator;
import com.zestflow.admin.registry.RegistryLiveStore;
import com.zestflow.admin.repository.ExecutorRegistryMapper;
import com.zestflow.admin.repository.ScheduleLogMapper;
import com.zestflow.admin.repository.UserAppRoleMapper;
import com.zestflow.admin.service.MailService;
import com.zestflow.common.protocol.EventStats;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExecutionSlaAlertServiceTest {

    @Mock private AlertConfigService alertConfigService;
    @Mock private UserAppRoleMapper userAppRoleMapper;
    @Mock private AlertRecipientService recipientService;
    @Mock private AlertCooldownService cooldownService;
    @Mock private AlertHistoryService alertHistoryService;
    @Mock private CollectorQueryAggregator collectorQueryAggregator;
    @Mock private ExecutorRegistryMapper executorRegistryMapper;
    @Mock private RegistryLiveStore liveStore;
    @Mock private ScheduleLogMapper scheduleLogMapper;
    @Mock private MailService mailService;

    @InjectMocks
    private ExecutionSlaAlertService service;

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
    }

    @AfterEach
    void tearDown() {
        com.zestflow.admin.config.TenantContextHolder.clear();
    }

    @Test
    void scan_sendsAlertWhenSuccessRateLow() {
        TenantAppScope scope = new TenantAppScope();
        scope.setTenantId(1L);
        scope.setAppCode("demo");
        when(userAppRoleMapper.selectDistinctTenantApps()).thenReturn(List.of(scope));

        EventStats stats = EventStats.builder()
                .executionCount(20)
                .successRate(80.0)
                .failCount(4)
                .p95CostMs(1000L)
                .build();
        when(collectorQueryAggregator.queryStats(any(), eq("demo"))).thenReturn(stats);
        when(executorRegistryMapper.selectCount(any())).thenReturn(0L);
        when(scheduleLogMapper.selectCount(any())).thenReturn(0L);
        when(recipientService.resolveRecipientEmails(1L, "demo")).thenReturn(List.of("owner@example.com"));
        when(cooldownService.shouldSend(any(), anyInt())).thenReturn(true);

        String summary = service.scan();

        verify(mailService).sendSlaAlertEmail(anyList(), any(SlaAlertMailContext.class));
        verify(alertHistoryService).record(eq(1L), eq("demo"), eq(AlertRule.LOW_SUCCESS_RATE),
                any(SlaAlertMailContext.class), anyList());
        verify(cooldownService).markSent(AlertCooldownService.buildKey(1L, "demo", AlertRule.LOW_SUCCESS_RATE));
        org.assertj.core.api.Assertions.assertThat(summary).contains("alerts=1");
    }

    @Test
    void scan_skipsWhenDisabled() {
        when(alertConfigService.resolveEffective(1L)).thenReturn(
                EffectiveAlertConfig.builder().enabled(false).build());
        TenantAppScope scope = new TenantAppScope();
        scope.setTenantId(1L);
        scope.setAppCode("demo");
        when(userAppRoleMapper.selectDistinctTenantApps()).thenReturn(List.of(scope));

        String summary = service.scan();

        org.assertj.core.api.Assertions.assertThat(summary).contains("alerts=0");
        verify(mailService, never()).sendSlaAlertEmail(anyList(), any());
    }

    @Test
    void scan_respectsCooldown() {
        TenantAppScope scope = new TenantAppScope();
        scope.setTenantId(1L);
        scope.setAppCode("demo");
        when(userAppRoleMapper.selectDistinctTenantApps()).thenReturn(List.of(scope));

        EventStats stats = EventStats.builder()
                .executionCount(20)
                .successRate(80.0)
                .failCount(4)
                .p95CostMs(1000L)
                .build();
        when(collectorQueryAggregator.queryStats(any(), eq("demo"))).thenReturn(stats);
        when(executorRegistryMapper.selectCount(any())).thenReturn(0L);
        when(scheduleLogMapper.selectCount(any())).thenReturn(0L);
        when(cooldownService.shouldSend(AlertCooldownService.buildKey(1L, "demo", AlertRule.LOW_SUCCESS_RATE), 30))
                .thenReturn(false);

        service.scan();

        verify(mailService, never()).sendSlaAlertEmail(anyList(), any());
    }
}
