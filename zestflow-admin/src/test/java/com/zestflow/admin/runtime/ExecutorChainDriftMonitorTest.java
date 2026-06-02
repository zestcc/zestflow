package com.zestflow.admin.runtime;

import com.zestflow.admin.client.ExecutorProxyService;
import com.zestflow.admin.model.entity.ExecutorRegistryPO;
import com.zestflow.admin.repository.ExecutorRegistryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExecutorChainDriftMonitorTest {

    @Mock
    private ExecutorRegistryMapper executorRegistryMapper;
    @Mock
    private ExecutorProxyService executorProxyService;
    @Mock
    private AdminRuntimeStateStore runtimeStateStore;
    @Mock
    private RestTemplate restTemplate;

    private ExecutorChainDriftMonitor monitor;

    @BeforeEach
    void setUp() {
        monitor = new ExecutorChainDriftMonitor(
                executorRegistryMapper, executorProxyService, runtimeStateStore, restTemplate);
        ReflectionTestUtils.setField(monitor, "protocol", "http");
        when(executorProxyService.executorHeaders()).thenReturn(new HttpHeaders());
        when(runtimeStateStore.getAllChainSync()).thenReturn(Map.of());
    }

    @Test
    void reconcileActiveChains_detectsDriftBetweenExecutors() {
        ExecutorRegistryPO e1 = executor("e1", "127.0.0.1", 20550);
        ExecutorRegistryPO e2 = executor("e2", "127.0.0.1", 20551);
        when(executorRegistryMapper.selectList(any())).thenReturn(List.of(e1, e2));

        when(restTemplate.exchange(
                eq("http://127.0.0.1:20550/api/chains/active-codes"),
                eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("[\"chain-a\"]"));
        when(restTemplate.exchange(
                eq("http://127.0.0.1:20551/api/chains/active-codes"),
                eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("[\"chain-b\"]"));

        monitor.reconcileActiveChains();

        ExecutorChainDriftMonitor.DriftSnapshot snapshot = monitor.getLastSnapshot();
        assertThat(snapshot.driftDetected()).isTrue();
        assertThat(snapshot.driftAppCodes()).contains("demo-app");
    }

    @Test
    void reconcileActiveChains_noDriftWhenSetsMatch() {
        ExecutorRegistryPO e1 = executor("e1", "127.0.0.1", 20550);
        ExecutorRegistryPO e2 = executor("e2", "127.0.0.1", 20551);
        when(executorRegistryMapper.selectList(any())).thenReturn(List.of(e1, e2));

        when(restTemplate.exchange(any(String.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("[\"chain-a\",\"chain-b\"]"));

        monitor.reconcileActiveChains();

        assertThat(monitor.getLastSnapshot().driftDetected()).isFalse();
    }

    @Test
    void reconcileActiveChains_skipsWhenOnlyOneExecutorOnline() {
        when(executorRegistryMapper.selectList(any()))
                .thenReturn(List.of(executor("e1", "127.0.0.1", 20550)));

        monitor.reconcileActiveChains();

        assertThat(monitor.getLastSnapshot().driftDetected()).isFalse();
    }

    @Test
    void reconcileActiveChains_treatsFetchFailureAsEmptySet() {
        ExecutorRegistryPO e1 = executor("e1", "127.0.0.1", 20550);
        ExecutorRegistryPO e2 = executor("e2", "127.0.0.1", 20551);
        when(executorRegistryMapper.selectList(any())).thenReturn(List.of(e1, e2));

        when(restTemplate.exchange(
                eq("http://127.0.0.1:20550/api/chains/active-codes"),
                eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("[\"chain-a\"]"));
        when(restTemplate.exchange(
                eq("http://127.0.0.1:20551/api/chains/active-codes"),
                eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RuntimeException("timeout"));

        monitor.reconcileActiveChains();

        assertThat(monitor.getLastSnapshot().driftDetected()).isTrue();
    }

    private static ExecutorRegistryPO executor(String id, String host, int port) {
        ExecutorRegistryPO po = new ExecutorRegistryPO();
        po.setExecutorId(id);
        po.setExecutorHost(host);
        po.setExecutorPort(port);
        po.setAppCode("demo-app");
        return po;
    }
}
