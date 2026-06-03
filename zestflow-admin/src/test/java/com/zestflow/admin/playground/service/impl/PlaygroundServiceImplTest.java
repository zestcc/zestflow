package com.zestflow.admin.playground.service.impl;

import com.zestflow.admin.playground.PlaygroundRateLimiter;
import com.zestflow.admin.playground.model.entity.PlaygroundScenePO;
import com.zestflow.admin.playground.repository.PlaygroundRecordMapper;
import com.zestflow.admin.playground.repository.PlaygroundSceneMapper;
import com.zestflow.admin.client.ExecutorProxyService;
import com.zestflow.admin.playground.support.PlaygroundAccessControl;
import com.zestflow.admin.playground.support.PlaygroundUrlResolver;
import com.zestflow.admin.service.TenantAppContext;
import org.junit.jupiter.api.BeforeEach;
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
    @Mock private PlaygroundAccessControl accessControl;
    @Mock private PlaygroundUrlResolver playgroundUrlResolver;

    private PlaygroundServiceImpl playgroundService;

    @BeforeEach
    void setUp() {
        playgroundService = new PlaygroundServiceImpl(
                sceneMapper, recordMapper, proxyService, rateLimiter,
                tenantAppContext, accessControl, playgroundUrlResolver);
        lenient().when(playgroundUrlResolver.allowedBaseUrls(anyString())).thenReturn(java.util.List.of());
        lenient().when(playgroundUrlResolver.stripInternalAbsoluteUrl(anyString())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(playgroundUrlResolver.isExecutePath(anyString())).thenAnswer(inv -> {
            String p = inv.getArgument(0);
            return "/execute".equals(p) || (p != null && p.contains("/execute"));
        });
        lenient().when(playgroundUrlResolver.isApiPath(anyString())).thenAnswer(inv -> {
            String p = inv.getArgument(0);
            return p != null && p.contains("/api/");
        });
        lenient().when(playgroundUrlResolver.isTomcatBusinessUrl(anyString(), anyString())).thenReturn(false);
    }

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
        assertThat(result.get("instanceId")).isEqualTo("inst-001");
    }

    @Test
    void executeScene_shouldExtractOrderIdFromBusinessApiResponse() {
        PlaygroundScenePO scene = createTestScene("SCN003");
        scene.setRequestPath("/api/orders/handleApplyAfterSale");
        scene.setRequestMethod("POST");
        when(sceneMapper.selectOne(any())).thenReturn(scene);
        when(rateLimiter.tryAcquire("SCN003", 30)).thenReturn(true);
        when(tenantAppContext.getCurrentTenantId()).thenReturn(1L);
        when(proxyService.executeOnExecutor(eq("playground-app"), eq("POST"),
                eq("/api/orders/handleApplyAfterSale"), anyString()))
                .thenReturn("{\"code\":200,\"data\":{\"orderId\":\"exec-abc123\",\"status\":\"SUCCESS\",\"costMs\":120}}");
        doAnswer(invocation -> { ((PlaygroundRecordPO) invocation.getArgument(0)).setId(1L); return 1; })
                .when(recordMapper).insert(any(PlaygroundRecordPO.class));

        Map<String, Object> result = playgroundService.executeScene("SCN003", Map.of("applyId", "1"), "10.0.0.1");

        assertThat(result.get("code")).isEqualTo(200);
        assertThat(result.get("status")).isEqualTo(1);
        assertThat(result.get("instanceId")).isEqualTo("exec-abc123");
    }

    @Test
    void executeScene_shouldProxyBusinessApiViaNetty() {
        PlaygroundScenePO scene = createTestScene("SCN002");
        scene.setRequestPath("/api/orders/handleApplyAfterSale");
        scene.setRequestMethod("POST");
        when(sceneMapper.selectOne(any())).thenReturn(scene);
        when(rateLimiter.tryAcquire("SCN002", 30)).thenReturn(true);
        when(tenantAppContext.getCurrentTenantId()).thenReturn(1L);
        when(proxyService.executeOnExecutor(eq("playground-app"), eq("POST"),
                eq("/api/orders/handleApplyAfterSale"), anyString()))
                .thenReturn("{\"code\":200,\"data\":\"ok\"}");
        doAnswer(invocation -> { ((PlaygroundRecordPO) invocation.getArgument(0)).setId(1L); return 1; })
                .when(recordMapper).insert(any(PlaygroundRecordPO.class));

        Map<String, Object> result = playgroundService.executeScene("SCN002", Map.of("orderId", "1"), "10.0.0.1");

        assertThat(result.get("code")).isEqualTo(200);
        assertThat(result.get("status")).isEqualTo(1);
        verify(proxyService).executeOnExecutor(eq("playground-app"), eq("POST"),
                eq("/api/orders/handleApplyAfterSale"), anyString());
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

        assertThat(result.get("code")).isEqualTo(200);
        assertThat(result.get("status")).isEqualTo(0);
        assertThat(result.get("errorMsg")).toString().contains("连接超时");
    }

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
