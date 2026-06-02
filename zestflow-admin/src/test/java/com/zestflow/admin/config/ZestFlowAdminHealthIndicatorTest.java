package com.zestflow.admin.config;

import com.zestflow.admin.repository.ExecutorRegistryMapper;
import com.zestflow.admin.runtime.AdminDeployProperties;
import com.zestflow.admin.service.CollectorRegistryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Status;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ZestFlowAdminHealthIndicatorTest {

    @Mock
    private CollectorRegistryService collectorRegistryService;
    @Mock
    private ExecutorRegistryMapper executorRegistryMapper;

    private AdminDeployProperties deployProperties;
    private AdminCacheProperties cacheProperties;
    private MockEnvironment environment;

    @BeforeEach
    void setUp() {
        deployProperties = new AdminDeployProperties();
        cacheProperties = new AdminCacheProperties();
        environment = new MockEnvironment();
    }

    @Test
    void health_standaloneWithoutRedis_isUpWhenNodesOnline() {
        deployProperties.setDeployMode("standalone");
        cacheProperties.setType("caffeine");
        when(collectorRegistryService.listAllOnline()).thenReturn(List.of(new com.zestflow.admin.model.vo.CollectorRegistryVO()));
        when(executorRegistryMapper.selectCount(any())).thenReturn(1L);

        ZestFlowAdminHealthIndicator indicator = new ZestFlowAdminHealthIndicator(
                deployProperties, cacheProperties, collectorRegistryService, executorRegistryMapper, environment);

        assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void health_clusterWithoutRedis_isDown() {
        environment.setProperty("zestflow.admin.deploy-mode", "cluster");
        when(collectorRegistryService.listAllOnline()).thenReturn(List.of());
        when(executorRegistryMapper.selectCount(any())).thenReturn(0L);

        ZestFlowAdminHealthIndicator indicator = new ZestFlowAdminHealthIndicator(
                deployProperties, cacheProperties, collectorRegistryService, executorRegistryMapper, environment);

        assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    void health_noOnlineNodes_isDegraded() {
        deployProperties.setDeployMode("standalone");
        when(collectorRegistryService.listAllOnline()).thenReturn(List.of());
        when(executorRegistryMapper.selectCount(any())).thenReturn(0L);

        ZestFlowAdminHealthIndicator indicator = new ZestFlowAdminHealthIndicator(
                deployProperties, cacheProperties, collectorRegistryService, executorRegistryMapper, environment);

        assertThat(indicator.health().getStatus()).isEqualTo(Status.DEGRADED);
    }
}
