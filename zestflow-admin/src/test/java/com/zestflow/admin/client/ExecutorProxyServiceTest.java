package com.zestflow.admin.client;

import com.zestflow.admin.model.entity.ExecutorRegistryPO;
import com.zestflow.admin.repository.ExecutorRegistryMapper;
import com.zestflow.common.constant.RegistryConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExecutorProxyServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private ExecutorRegistryMapper executorRegistryMapper;

    private ExecutorProxyService proxyService;

    @BeforeEach
    void setUp() {
        proxyService = new ExecutorProxyService(restTemplate, executorRegistryMapper);
        ReflectionTestUtils.setField(proxyService, "protocol", "http");
    }

    @Test
    void resolveExecutorBaseUrl_roundRobinCyclesThroughExecutors() {
        ExecutorRegistryPO e1 = executor("host-a", 20550);
        ExecutorRegistryPO e2 = executor("host-b", 20550);
        ExecutorRegistryPO e3 = executor("host-c", 20550);
        when(executorRegistryMapper.selectList(any())).thenReturn(List.of(e1, e2, e3));

        String u1 = proxyService.resolveExecutorBaseUrl("app");
        String u2 = proxyService.resolveExecutorBaseUrl("app");
        String u3 = proxyService.resolveExecutorBaseUrl("app");
        String u4 = proxyService.resolveExecutorBaseUrl("app");
        String u5 = proxyService.resolveExecutorBaseUrl("app");
        String u6 = proxyService.resolveExecutorBaseUrl("app");

        assertThat(u1).isEqualTo("http://host-a:20550");
        assertThat(u2).isEqualTo("http://host-b:20550");
        assertThat(u3).isEqualTo("http://host-c:20550");
        assertThat(u4).isEqualTo("http://host-a:20550");
        assertThat(u5).isEqualTo("http://host-b:20550");
        assertThat(u6).isEqualTo("http://host-c:20550");
    }

    @Test
    void resolveExecutorBaseUrl_singleExecutor_alwaysReturnsSame() {
        ExecutorRegistryPO e1 = executor("host-a", 20550);
        when(executorRegistryMapper.selectList(any())).thenReturn(List.of(e1));

        String u1 = proxyService.resolveExecutorBaseUrl("app");
        String u2 = proxyService.resolveExecutorBaseUrl("app");
        String u3 = proxyService.resolveExecutorBaseUrl("app");

        assertThat(u1).isEqualTo("http://host-a:20550");
        assertThat(u2).isEqualTo("http://host-a:20550");
        assertThat(u3).isEqualTo("http://host-a:20550");
    }

    @Test
    void resolveExecutorBaseUrl_noOnlineExecutors_returnsNull() {
        when(executorRegistryMapper.selectList(any())).thenReturn(List.of());

        String url = proxyService.resolveExecutorBaseUrl("app");

        assertThat(url).isNull();
    }

    @Test
    void resolveExecutorBaseUrl_nullAppCode_returnsNull() {
        String url = proxyService.resolveExecutorBaseUrl(null);

        assertThat(url).isNull();
    }

    @Test
    void resolveExecutorBaseUrl_blankAppCode_returnsNull() {
        String url = proxyService.resolveExecutorBaseUrl("  ");

        assertThat(url).isNull();
    }

    private static ExecutorRegistryPO executor(String host, int port) {
        ExecutorRegistryPO po = new ExecutorRegistryPO();
        po.setExecutorHost(host);
        po.setExecutorPort(port);
        po.setStatus(RegistryConstants.STATUS_ONLINE);
        return po;
    }
}
