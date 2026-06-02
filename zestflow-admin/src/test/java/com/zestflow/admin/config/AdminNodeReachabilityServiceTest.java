package com.zestflow.admin.config;

import com.zestflow.admin.model.entity.ExecutorRegistryPO;
import com.zestflow.admin.model.vo.CollectorRegistryVO;
import com.zestflow.admin.repository.ExecutorRegistryMapper;
import com.zestflow.admin.service.CollectorRegistryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminNodeReachabilityServiceTest {

    @Mock
    private RestTemplate restTemplate;
    @Mock
    private CollectorRegistryService collectorRegistryService;
    @Mock
    private ExecutorRegistryMapper executorRegistryMapper;

    private AdminNodeReachabilityService service;

    @BeforeEach
    void setUp() {
        service = new AdminNodeReachabilityService(restTemplate, collectorRegistryService, executorRegistryMapper);
        ReflectionTestUtils.setField(service, "protocol", "http");
        ReflectionTestUtils.setField(service, "probeEnabled", true);
    }

    @Test
    void probeRegisteredNodes_countsReachableNodes() {
        ExecutorRegistryPO executor = new ExecutorRegistryPO();
        executor.setExecutorId("e1");
        executor.setExecutorHost("127.0.0.1");
        executor.setExecutorPort(20550);
        when(executorRegistryMapper.selectList(any())).thenReturn(List.of(executor));

        CollectorRegistryVO collector = CollectorRegistryVO.builder()
                .collectorId("c1")
                .collectorHost("127.0.0.1")
                .collectorPort(20650)
                .build();
        when(collectorRegistryService.listAllOnline()).thenReturn(List.of(collector));

        when(restTemplate.exchange(any(String.class), eq(HttpMethod.GET), eq(HttpEntity.EMPTY), eq(String.class)))
                .thenReturn(ResponseEntity.ok("ok"));

        AdminNodeReachabilityService.NodeProbeSummary summary = service.probeRegisteredNodes();

        assertThat(summary.executorsReachable()).isEqualTo(1);
        assertThat(summary.collectorsReachable()).isEqualTo(1);
        assertThat(summary.hasUnreachableNodes()).isFalse();
    }

    @Test
    void probeRegisteredNodes_whenProbeDisabled_returnsDisabledSummary() {
        ReflectionTestUtils.setField(service, "probeEnabled", false);

        AdminNodeReachabilityService.NodeProbeSummary summary = service.probeRegisteredNodes();

        assertThat(summary.enabled()).isFalse();
        assertThat(summary.executorsRegistered()).isZero();
        assertThat(summary.hasUnreachableNodes()).isFalse();
    }

    @Test
    void probeRegisteredNodes_marksUnreachableExecutor() {
        ExecutorRegistryPO executor = new ExecutorRegistryPO();
        executor.setExecutorId("e-down");
        executor.setExecutorHost("10.0.0.99");
        executor.setExecutorPort(20550);
        when(executorRegistryMapper.selectList(any())).thenReturn(List.of(executor));
        when(collectorRegistryService.listAllOnline()).thenReturn(List.of());

        when(restTemplate.exchange(any(String.class), eq(HttpMethod.GET), eq(HttpEntity.EMPTY), eq(String.class)))
                .thenThrow(new org.springframework.web.client.ResourceAccessException("connection refused"));

        AdminNodeReachabilityService.NodeProbeSummary summary = service.probeRegisteredNodes();

        assertThat(summary.executorsUnreachable()).isEqualTo(1);
        assertThat(summary.unreachableExecutorIds()).containsExactly("e-down");
        assertThat(summary.hasUnreachableNodes()).isTrue();
    }
}
