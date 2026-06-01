package com.zestflow.admin.service.impl;

import com.zestflow.admin.client.CollectorClient;
import com.zestflow.admin.client.ExecutorProxyService;
import com.zestflow.admin.model.entity.ExecutorRegistryPO;
import com.zestflow.admin.model.vo.DashboardStatsVO;
import com.zestflow.admin.repository.ExecutorRegistryMapper;
import com.zestflow.admin.service.TenantAppContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DashboardServiceImplTest {

    @Mock private ExecutorRegistryMapper executorRegistryMapper;
    @Mock private ExecutorProxyService proxyService;
    @Mock private CollectorClient collectorClient;
    @Mock private TenantAppContext tenantAppContext;

    private DashboardServiceImpl dashboardService;

    @BeforeEach
    void setUp() {
        dashboardService = new DashboardServiceImpl(
                executorRegistryMapper, proxyService, collectorClient, tenantAppContext);
    }

    @Test
    void getStats_superAdmin_noAppFilter() {
        when(executorRegistryMapper.selectCount(any())).thenReturn(2L, 2L, 2L, 0L, 0L, 1L);
        ExecutorRegistryPO appPo = new ExecutorRegistryPO();
        appPo.setAppCode("app-a");
        appPo.setAppName("应用A");
        when(executorRegistryMapper.selectList(any())).thenReturn(List.of(appPo));
        when(tenantAppContext.getCurrentUserAppCodes()).thenReturn(Collections.emptySet());
        // 代理调用顺序：/api/chains → /api/designs → /api/chains?status=4
        when(proxyService.getFromExecutor(eq("app-a"), anyString(), anyString()))
                .thenReturn("{\"total\":10}")   // chainJson → totalChains
                .thenReturn("{\"total\":3}")    // designJson → totalDesigns
                .thenReturn("{\"total\":5}");   // enabledJson → enabledChains
        when(collectorClient.queryStats(anyLong(), any(), anyLong())).thenReturn(
                Collections.singletonMap("totalCount", 100));

        DashboardStatsVO stats = dashboardService.getStats();

        assertThat(stats.getTotalApps()).isEqualTo(2L);
        assertThat(stats.getTotalChains()).isEqualTo(10L);
        assertThat(stats.getTotalDesigns()).isEqualTo(3L);
        assertThat(stats.getTodayExecutions()).isEqualTo(100L);
    }

    @Test
    void getStats_normalUser_filtersApps() {
        when(executorRegistryMapper.selectCount(any())).thenReturn(2L, 2L, 2L, 0L, 0L, 1L);
        ExecutorRegistryPO appA = new ExecutorRegistryPO();
        appA.setAppCode("app-a");
        ExecutorRegistryPO appB = new ExecutorRegistryPO();
        appB.setAppCode("app-b");
        when(executorRegistryMapper.selectList(any())).thenReturn(List.of(appA, appB));
        // 非超管，只可见 app-a
        when(tenantAppContext.getCurrentUserAppCodes()).thenReturn(Set.of("app-a"));
        // 代理调用顺序：/api/chains → /api/designs → /api/chains?status=4
        when(proxyService.getFromExecutor(eq("app-a"), anyString(), anyString()))
                .thenReturn("{\"total\":5}")
                .thenReturn("{\"total\":2}")
                .thenReturn("{\"total\":3}");
        when(collectorClient.queryStats(anyLong(), any(), anyLong())).thenReturn(Collections.emptyMap());

        DashboardStatsVO stats = dashboardService.getStats();

        assertThat(stats.getTotalApps()).isEqualTo(2L);
        // 只应查询 app-a（app-b 被过滤）
        verify(proxyService, atMost(3)).getFromExecutor(anyString(), anyString(), anyString());
    }

    @Test
    void getStats_noOnlineExecutors_skipsProxyCalls() {
        when(executorRegistryMapper.selectCount(any())).thenReturn(2L, 2L, 2L, 0L, 0L, 0L);
        ExecutorRegistryPO appPo = new ExecutorRegistryPO();
        appPo.setAppCode("app-a");
        when(executorRegistryMapper.selectList(any())).thenReturn(List.of(appPo));
        when(tenantAppContext.getCurrentUserAppCodes()).thenReturn(Collections.emptySet());
        // 在线执行器数为 0
        when(collectorClient.queryStats(anyLong(), any(), anyLong())).thenReturn(Collections.emptyMap());

        DashboardStatsVO stats = dashboardService.getStats();

        // 无在线执行器，不查 Executor 数据
        verify(proxyService, never()).getFromExecutor(anyString(), anyString(), anyString());
        assertThat(stats.getTotalChains()).isZero();
    }

    @Test
    void getStats_collectorFailure_gracefullyDegrades() {
        when(executorRegistryMapper.selectCount(any())).thenReturn(2L, 2L, 2L, 0L, 0L, 1L);
        ExecutorRegistryPO appPo = new ExecutorRegistryPO();
        appPo.setAppCode("app-a");
        when(executorRegistryMapper.selectList(any())).thenReturn(List.of(appPo));
        when(tenantAppContext.getCurrentUserAppCodes()).thenReturn(Collections.emptySet());
        when(proxyService.getFromExecutor(anyString(), anyString(), anyString()))
                .thenReturn("{\"total\":10}");
        when(collectorClient.queryStats(anyLong(), any(), anyLong()))
                .thenThrow(new RuntimeException("Collector unavailable"));

        DashboardStatsVO stats = dashboardService.getStats();

        assertThat(stats.getTodayExecutions()).isZero();
        assertThat(stats.getAvgExecutionMs()).isZero();
    }

    @Test
    void getStats_proxyFailure_propagates() {
        when(executorRegistryMapper.selectCount(any())).thenReturn(2L, 2L, 2L, 0L, 0L, 1L);
        ExecutorRegistryPO appPo = new ExecutorRegistryPO();
        appPo.setAppCode("app-a");
        when(executorRegistryMapper.selectList(any())).thenReturn(List.of(appPo));
        when(tenantAppContext.getCurrentUserAppCodes()).thenReturn(Collections.emptySet());
        when(proxyService.getFromExecutor(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("Executor unavailable"));

        assertThatThrownBy(() -> dashboardService.getStats())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Executor unavailable");
    }
}
