package com.zestflow.admin.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zestflow.admin.model.entity.ExecutorRegistryPO;
import com.zestflow.admin.repository.ExecutorRegistryMapper;
import com.zestflow.common.constant.RegistryConstants;
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
class ExecutorProxyServiceMergePagingTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

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
    void getFromExecutor_mergesAndSlicesByClientPage() throws Exception {
        ExecutorRegistryPO e1 = executor("exec-a", "host-a", 20550);
        ExecutorRegistryPO e2 = executor("exec-b", "host-b", 20550);
        when(executorRegistryMapper.selectList(any())).thenReturn(List.of(e1, e2));

        when(restTemplate.exchange(eq("http://host-a:20550/api/chains?page=1&size=500"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(pageJson("chain-a", "2026-01-03")));
        when(restTemplate.exchange(eq("http://host-b:20550/api/chains?page=1&size=500"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(pageJson("chain-b", "2026-01-02")));

        String json = proxyService.getFromExecutor("app", "/api/chains", "?page=1&size=1");
        JsonNode root = MAPPER.readTree(json);

        assertThat(root.get("total").asInt()).isEqualTo(2);
        assertThat(root.get("current").asInt()).isEqualTo(1);
        assertThat(root.get("size").asInt()).isEqualTo(1);
        assertThat(root.get("records")).hasSize(1);
        assertThat(root.get("records").get(0).get("code").asText()).isEqualTo("chain-a");
    }

    @Test
    void getFromExecutor_mergesSecondPageSlice() throws Exception {
        ExecutorRegistryPO e1 = executor("exec-a", "host-a", 20550);
        ExecutorRegistryPO e2 = executor("exec-b", "host-b", 20550);
        when(executorRegistryMapper.selectList(any())).thenReturn(List.of(e1, e2));

        when(restTemplate.exchange(eq("http://host-a:20550/api/chains?page=1&size=500"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(pageJson("chain-a", "2026-01-03")));
        when(restTemplate.exchange(eq("http://host-b:20550/api/chains?page=1&size=500"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(pageJson("chain-b", "2026-01-02")));

        String json = proxyService.getFromExecutor("app", "/api/chains", "?page=2&size=1");
        JsonNode root = MAPPER.readTree(json);

        assertThat(root.get("records")).hasSize(1);
        assertThat(root.get("records").get(0).get("code").asText()).isEqualTo("chain-b");
    }

    @Test
    void getFromExecutor_mergesComponentsByComponentId() throws Exception {
        ExecutorRegistryPO e1 = executor("exec-a", "host-a", 20550);
        ExecutorRegistryPO e2 = executor("exec-b", "host-b", 20550);
        when(executorRegistryMapper.selectList(any())).thenReturn(List.of(e1, e2));

        when(restTemplate.exchange(eq("http://host-a:20550/api/components?page=1&size=500"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(componentPageJson("comp.order.create", "2026-01-03")));
        when(restTemplate.exchange(eq("http://host-b:20550/api/components?page=1&size=500"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(componentPageJson("comp.order.create", "2026-01-02")));

        String json = proxyService.getFromExecutor("app", "/api/components", "?page=1&size=10");
        JsonNode root = MAPPER.readTree(json);

        assertThat(root.get("total").asInt()).isEqualTo(1);
        assertThat(root.get("records")).hasSize(1);
        assertThat(root.get("records").get(0).get("componentId").asText()).isEqualTo("comp.order.create");
    }

    private static String componentPageJson(String componentId, String cachedAt) {
        return String.format(
                "{\"records\":[{\"componentId\":\"%s\",\"componentName\":\"测试元件\",\"cachedAt\":\"%s\"}],\"total\":1,\"current\":1,\"size\":10}",
                componentId, cachedAt);
    }

    private static String pageJson(String code, String updatedAt) {
        return String.format(
                "{\"records\":[{\"code\":\"%s\",\"updatedAt\":\"%s\"}],\"total\":1,\"current\":1,\"size\":10}",
                code, updatedAt);
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
