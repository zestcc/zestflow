package com.zestflow.admin.client;

import com.zestflow.admin.model.entity.ExecutorRegistryPO;
import com.zestflow.admin.repository.ExecutorRegistryMapper;
import com.zestflow.common.constant.RegistryConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Phase 2b P-04 — 10 实例轮询均匀性门禁（±10%，对标 Spring Cloud LoadBalancer 轮询语义）。
 */
@Tag("perf")
@ExtendWith(MockitoExtension.class)
class RoundRobinDistributionGateTest {

    private static final int EXECUTOR_COUNT = 10;
    private static final int SAMPLES = 1000;
    /** 理想每实例 100 次，允许 90~110 */
    private static final int MIN_HITS = 90;
    private static final int MAX_HITS = 110;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private ExecutorRegistryMapper executorRegistryMapper;

    private ExecutorProxyService proxyService;

    @BeforeEach
    void setUp() {
        proxyService = new ExecutorProxyService(restTemplate, executorRegistryMapper);
        ReflectionTestUtils.setField(proxyService, "protocol", "http");

        List<ExecutorRegistryPO> executors = IntStream.range(0, EXECUTOR_COUNT)
                .mapToObj(i -> executor("host-" + i, 20550 + i))
                .toList();
        when(executorRegistryMapper.selectList(any())).thenReturn(executors);
    }

    @Test
    void roundRobin_tenExecutors_uniformWithinTenPercent() {
        Map<String, Integer> hits = new HashMap<>();

        for (int i = 0; i < SAMPLES; i++) {
            String url = proxyService.resolveExecutorBaseUrl("demo-app");
            assertThat(url).isNotNull();
            hits.merge(url, 1, Integer::sum);
        }

        assertThat(hits).hasSize(EXECUTOR_COUNT);
        hits.values().forEach(count -> assertThat(count)
                .as("each executor should receive ~%d of %d requests", SAMPLES / EXECUTOR_COUNT, SAMPLES)
                .isBetween(MIN_HITS, MAX_HITS));
    }

    private static ExecutorRegistryPO executor(String host, int port) {
        ExecutorRegistryPO po = new ExecutorRegistryPO();
        po.setExecutorHost(host);
        po.setExecutorPort(port);
        po.setAppCode("demo-app");
        po.setStatus(RegistryConstants.STATUS_ONLINE);
        return po;
    }
}
