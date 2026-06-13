package com.zestflow.admin.client;

import com.zestflow.admin.client.cache.NoopExecutorReadCache;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.RequestEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExecutorProxyServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private ExecutorRegistryMapper executorRegistryMapper;

    private ExecutorProxyService proxyService;
    private RegistryLiveStore liveStore;

    @BeforeEach
    void setUp() {
        liveStore = new InMemoryRegistryLiveStore();
        proxyService = new ExecutorProxyService(restTemplate, executorRegistryMapper, liveStore, new NoopExecutorReadCache());
        ReflectionTestUtils.setField(proxyService, "protocol", "http");
    }

    @Test
    void resolveExecutorBaseUrl_usesPrimaryExecutorDeterministically() {
        ExecutorRegistryPO e1 = executor("exec-b", "host-b", 20550);
        ExecutorRegistryPO e2 = executor("exec-a", "host-a", 20550);
        ExecutorRegistryPO e3 = executor("exec-c", "host-c", 20550);
        liveStore.touchExecutor("exec-a");
        liveStore.touchExecutor("exec-b");
        liveStore.touchExecutor("exec-c");
        when(executorRegistryMapper.selectList(any())).thenReturn(List.of(e1, e2, e3));

        String u1 = proxyService.resolveExecutorBaseUrl("app");
        String u2 = proxyService.resolveExecutorBaseUrl("app");

        assertThat(u1).isEqualTo("http://host-a:20550");
        assertThat(u2).isEqualTo("http://host-a:20550");
    }

    @Test
    void resolveExecutorBaseUrl_singleExecutor_alwaysReturnsSame() {
        ExecutorRegistryPO e1 = executor("exec-a", "host-a", 20550);
        liveStore.touchExecutor("exec-a");
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

    @Test
    void executeOnExecutor_post_shouldAttachAccessTokenHeader() {
        ReflectionTestUtils.setField(proxyService, "executorAccessToken", "exec-secret");
        liveStore.touchExecutor("exec-a");
        when(executorRegistryMapper.selectList(any())).thenReturn(List.of(executor("exec-a", "host-a", 20550)));
        when(restTemplate.postForObject(any(String.class), any(HttpEntity.class), eq(String.class)))
                .thenReturn("{\"code\":200}");

        proxyService.executeOnExecutor("app", "POST", "/api/orders/demo", "{}");

        verify(restTemplate).postForObject(
                eq("http://host-a:20550/api/orders/demo"),
                org.mockito.ArgumentMatchers.<HttpEntity<String>>argThat(entity ->
                        "exec-secret".equals(entity.getHeaders().getFirst("X-Access-Token"))),
                eq(String.class));
    }

    @Test
    void executeOnExecutor_get_shouldAttachAccessTokenHeader() throws Exception {
        ReflectionTestUtils.setField(proxyService, "executorAccessToken", "exec-secret");
        liveStore.touchExecutor("exec-a");
        when(executorRegistryMapper.selectList(any())).thenReturn(List.of(executor("exec-a", "host-a", 20550)));
        when(restTemplate.exchange(any(RequestEntity.class), eq(String.class)))
                .thenReturn(org.springframework.http.ResponseEntity.ok("{}"));

        proxyService.executeOnExecutor("app", "GET", "/api/orders/demo", null);

        verify(restTemplate).exchange(
                org.mockito.ArgumentMatchers.<RequestEntity<?>>argThat(req ->
                        req.getMethod() == HttpMethod.GET
                                && "exec-secret".equals(req.getHeaders().getFirst("X-Access-Token"))
                                && req.getUrl().equals(URI.create("http://host-a:20550/api/orders/demo"))),
                eq(String.class));
    }

    @Test
    void executeOnExecutor_post_multiExecutor_callsSingleInstanceOnly() {
        ExecutorRegistryPO e1 = executor("exec-a", "host-a", 20550);
        ExecutorRegistryPO e2 = executor("exec-b", "host-b", 20551);
        liveStore.touchExecutor("exec-a");
        liveStore.touchExecutor("exec-b");
        when(executorRegistryMapper.selectList(any())).thenReturn(List.of(e1, e2));
        when(restTemplate.postForObject(any(String.class), any(HttpEntity.class), eq(String.class)))
                .thenReturn("{\"code\":200}");

        proxyService.executeOnExecutor("app", "POST", "/api/orders/handleApplyAfterSale", "{}");

        verify(restTemplate).postForObject(
                eq("http://host-a:20550/api/orders/handleApplyAfterSale"),
                any(HttpEntity.class),
                eq(String.class));
        verify(restTemplate, org.mockito.Mockito.times(1))
                .postForObject(any(String.class), any(HttpEntity.class), eq(String.class));
    }

    @Test
    void looksLikeJsonBody_detectsJsonAndNonJson() {
        assertThat(ExecutorProxyService.looksLikeJsonBody("{\"a\":1}")).isTrue();
        assertThat(ExecutorProxyService.looksLikeJsonBody("[1,2]")).isTrue();
        assertThat(ExecutorProxyService.looksLikeJsonBody("  {\"a\":1}")).isTrue();
        assertThat(ExecutorProxyService.looksLikeJsonBody("<Response></Response>")).isFalse();
        assertThat(ExecutorProxyService.looksLikeJsonBody("plain text")).isFalse();
        assertThat(ExecutorProxyService.looksLikeJsonBody("")).isFalse();
    }

    @Test
    void executeOnExecutor_returnsXmlBodyUnchanged() {
        ExecutorRegistryPO e1 = executor("exec-a", "host-a", 20550);
        liveStore.touchExecutor("exec-a");
        when(executorRegistryMapper.selectList(any())).thenReturn(List.of(e1));
        String xml = "<Response><hotel id=\"1\"/></Response>";
        when(restTemplate.postForObject(any(String.class), any(HttpEntity.class), eq(String.class)))
                .thenReturn(xml);

        String result = proxyService.executeOnExecutor("app", "POST", "/api/heytrip/ota/rc/getHotels", "{}");

        assertThat(result).isEqualTo(xml);
    }

    private static ExecutorRegistryPO executor(String executorId, String host, int port) {
        ExecutorRegistryPO po = new ExecutorRegistryPO();
        po.setExecutorId(executorId);
        po.setExecutorHost(host);
        po.setExecutorPort(port);
        po.setStatus(RegistryConstants.STATUS_ONLINE);
        return po;
    }
}
