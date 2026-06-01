package com.zestflow.admin.playground.service.impl;

import com.zestflow.admin.playground.PlaygroundRateLimiter;
import com.zestflow.admin.playground.model.entity.PlaygroundScenePO;
import com.zestflow.admin.playground.repository.PlaygroundRecordMapper;
import com.zestflow.admin.playground.repository.PlaygroundSceneMapper;
import com.zestflow.admin.client.ExecutorProxyService;
import com.zestflow.admin.service.TenantAppContext;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.web.client.RestTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.zestflow.admin.playground.model.entity.PlaygroundRecordPO;

@ExtendWith(MockitoExtension.class)
class PlaygroundServiceImplTest {

    @Mock private PlaygroundSceneMapper sceneMapper;
    @Mock private PlaygroundRecordMapper recordMapper;
    @Mock private ExecutorProxyService proxyService;
    @Mock private PlaygroundRateLimiter rateLimiter;
    @Mock private TenantAppContext tenantAppContext;
    @Mock private RestTemplate restTemplate;

    private PlaygroundServiceImpl playgroundService;

    @BeforeEach
    void setUp() {
        playgroundService = new PlaygroundServiceImpl(
                sceneMapper, recordMapper, proxyService, rateLimiter, tenantAppContext, restTemplate);
        org.springframework.test.util.ReflectionTestUtils.setField(playgroundService, "defaultAppCode", "playground-app");
    }

    // ==================== executeScene ====================

    @Test
    void executeScene_shouldReturn404_whenSceneNotFound() {
        when(sceneMapper.selectOne(any())).thenReturn(null);

        Map<String, Object> result = playgroundService.executeScene("UNKNOWN", Map.of(), "127.0.0.1");

        assertThat(result.get("code")).isEqualTo(404);
        assertThat(result.get("message")).toString().contains("场景不存在");
    }

    @Test
    void executeScene_shouldReturn429_whenRateLimited() {
        PlaygroundScenePO scene = createTestScene("SCN001");
        when(sceneMapper.selectOne(any())).thenReturn(scene);
        when(rateLimiter.tryAcquire("SCN001", 30)).thenReturn(false);

        Map<String, Object> result = playgroundService.executeScene("SCN001", Map.of(), "127.0.0.1");

        assertThat(result.get("code")).isEqualTo(429);
    }

    @Test
    void executeScene_shouldReturnSuccess_whenExecutorResponds() {
        PlaygroundScenePO scene = createTestScene("SCN001");
        when(sceneMapper.selectOne(any())).thenReturn(scene);
        when(rateLimiter.tryAcquire("SCN001", 30)).thenReturn(true);
        when(tenantAppContext.getCurrentTenantId()).thenReturn(1L);
        when(proxyService.executeOnExecutor(anyString(), anyString(), anyString(), anyString()))
                .thenReturn("{\"instanceId\":\"inst-001\",\"status\":4,\"data\":\"ok\"}");
        doAnswer(invocation -> { ((PlaygroundRecordPO) invocation.getArgument(0)).setId(1L); return 1; })
                .when(recordMapper).insert(any(PlaygroundRecordPO.class));

        Map<String, Object> result = playgroundService.executeScene("SCN001", Map.of("msg", "hello"), "10.0.0.1");

        assertThat(result.get("code")).isEqualTo(200);
        assertThat(result.get("status")).isEqualTo(1);
        assertThat(result.get("logId")).isNotNull();
        assertThat(result.get("instanceId")).isEqualTo("inst-001");
    }

    @Test
    void executeScene_shouldHandleExecutorError() {
        PlaygroundScenePO scene = createTestScene("SCN001");
        when(sceneMapper.selectOne(any())).thenReturn(scene);
        when(rateLimiter.tryAcquire("SCN001", 30)).thenReturn(true);
        when(tenantAppContext.getCurrentTenantId()).thenReturn(1L);
        when(proxyService.executeOnExecutor(anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("连接超时"));
        doAnswer(invocation -> { ((PlaygroundRecordPO) invocation.getArgument(0)).setId(1L); return 1; })
                .when(recordMapper).insert(any(PlaygroundRecordPO.class));

        Map<String, Object> result = playgroundService.executeScene("SCN001", Map.of(), "10.0.0.1");

        assertThat(result.get("code")).isEqualTo(200); // still 200 — we return partial success
        assertThat(result.get("status")).isEqualTo(0); // failed
        assertThat(result.get("errorMsg")).toString().contains("连接超时");
    }

    // ==================== getSceneInfo ====================

    @Test
    void getSceneInfo_shouldReturnVO_whenFound() {
        PlaygroundScenePO po = createTestScene("SCN001");
        when(sceneMapper.selectOne(any())).thenReturn(po);

        var vo = playgroundService.getSceneInfo("SCN001");

        assertThat(vo).isNotNull();
        assertThat(vo.getSceneCode()).isEqualTo("SCN001");
        assertThat(vo.getRequestPath()).isEqualTo("/execute");
    }

    @Test
    void getSceneInfo_shouldReturnNull_whenNotFound() {
        when(sceneMapper.selectOne(any())).thenReturn(null);

        var vo = playgroundService.getSceneInfo("UNKNOWN");

        assertThat(vo).isNull();
    }

    private PlaygroundScenePO createTestScene(String code) {
        PlaygroundScenePO po = new PlaygroundScenePO();
        po.setId(1L);
        po.setSceneCode(code);
        po.setName("测试场景");
        po.setRequestPath("/execute");
        po.setRequestMethod("POST");
        po.setBodyType("JSON");
        po.setChainCode("CHN_TEST");
        po.setRateLimit(30);
        po.setAppCode("playground-app");
        return po;
    }
}
