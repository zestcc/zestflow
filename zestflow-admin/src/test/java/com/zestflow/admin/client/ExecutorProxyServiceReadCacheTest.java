package com.zestflow.admin.client;

import com.zestflow.admin.client.cache.CaffeineExecutorReadCache;
import com.zestflow.admin.client.cache.ExecutorReadCache;
import com.zestflow.admin.client.cache.ExecutorReadCacheProperties;
import com.zestflow.admin.model.entity.ExecutorRegistryPO;
import com.zestflow.admin.registry.InMemoryRegistryLiveStore;
import com.zestflow.admin.registry.RegistryLiveStore;
import com.zestflow.admin.repository.ExecutorRegistryMapper;
import com.zestflow.common.constant.RegistryConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExecutorProxyServiceReadCacheTest {

    @Mock
    private RestTemplate restTemplate;
    @Mock
    private ExecutorRegistryMapper executorRegistryMapper;

    private ExecutorProxyService proxyService;
    private CaffeineExecutorReadCache readCache;
    private RegistryLiveStore liveStore;

    @BeforeEach
    void setUp() {
        liveStore = new InMemoryRegistryLiveStore();
        ExecutorReadCacheProperties props = new ExecutorReadCacheProperties();
        readCache = new CaffeineExecutorReadCache(props);
        proxyService = new ExecutorProxyService(restTemplate, executorRegistryMapper, liveStore, readCache);
        ReflectionTestUtils.setField(proxyService, "protocol", "http");
    }

    @Test
    void getFromExecutor_whenOffline_returnsCachedSnapshotWithMeta() {
        ExecutorRegistryPO executor = executor("demo-app@127.0.0.1:20550", "127.0.0.1", 20550);
        liveStore.touchExecutor(executor.getExecutorId());
        when(executorRegistryMapper.selectList(any())).thenReturn(List.of(executor));

        String cached = "{\"records\":[{\"code\":\"CHN001\",\"name\":\"demo\"}],\"total\":1,\"current\":1,\"size\":10}";
        readCache.put(ExecutorReadCache.buildKey("demo-app", "/api/chains", "?page=1&size=10"), cached);

        when(restTemplate.exchange(any(String.class), eq(org.springframework.http.HttpMethod.GET), any(), eq(String.class)))
                .thenThrow(new ResourceAccessException("connection refused"));

        String json = proxyService.getFromExecutor("demo-app", "/api/chains", "?page=1&size=10");

        assertThat(json).contains("CHN001");
        assertThat(json).contains("\"_readCache\"");
        assertThat(json).contains("\"stale\":true");
    }

    @Test
    void getFromExecutor_whenNoOnlineExecutor_returnsCachedSnapshot() {
        String cached = "{\"records\":[{\"code\":\"CHN002\"}],\"total\":1,\"current\":1,\"size\":10}";
        readCache.put(ExecutorReadCache.buildKey("demo-app", "/api/chains", "?page=1&size=10"), cached);

        String json = proxyService.getFromExecutor("demo-app", "/api/chains", "?page=1&size=10");

        assertThat(json).contains("CHN002");
        assertThat(json).contains("\"stale\":true");
    }

    private static ExecutorRegistryPO executor(String id, String host, int port) {
        ExecutorRegistryPO po = new ExecutorRegistryPO();
        po.setExecutorId(id);
        po.setExecutorHost(host);
        po.setExecutorPort(port);
        po.setAppCode("demo-app");
        po.setStatus(RegistryConstants.STATUS_ONLINE);
        return po;
    }
}
