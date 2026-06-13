package com.zestflow.admin.client;

import com.zestflow.admin.client.cache.CaffeineExecutorReadCache;
import com.zestflow.admin.client.cache.ExecutorReadCacheProperties;
import com.zestflow.admin.registry.InMemoryRegistryLiveStore;
import com.zestflow.admin.registry.RegistryLiveStore;
import com.zestflow.admin.repository.ExecutorRegistryMapper;
import com.zestflow.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class ExecutorProxyServiceOfflineWriteTest {

    @Mock
    private RestTemplate restTemplate;
    @Mock
    private ExecutorRegistryMapper executorRegistryMapper;

    private ExecutorProxyService proxyService;

    @BeforeEach
    void setUp() {
        RegistryLiveStore liveStore = new InMemoryRegistryLiveStore();
        CaffeineExecutorReadCache readCache = new CaffeineExecutorReadCache(new ExecutorReadCacheProperties());
        proxyService = new ExecutorProxyService(restTemplate, executorRegistryMapper, liveStore, readCache);
        ReflectionTestUtils.setField(proxyService, "protocol", "http");
    }

    @Test
    void executeOnExecutor_postWhenOffline_throwsBizException() {
        assertThatThrownBy(() -> proxyService.executeOnExecutor("demo-app", "POST", "/api/chains", "{}"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("离线");
    }

    @Test
    void broadcastToExecutors_whenOffline_throwsBizException() {
        assertThatThrownBy(() -> proxyService.broadcastToExecutors("demo-app", "POST", "/reload", "{}"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("离线");
    }
}
