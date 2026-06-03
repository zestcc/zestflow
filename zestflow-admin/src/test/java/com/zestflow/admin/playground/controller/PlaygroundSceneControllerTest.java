package com.zestflow.admin.playground.controller;

import com.zestflow.admin.client.ExecutorProxyService;
import com.zestflow.admin.playground.support.PlaygroundAccessControl;
import com.zestflow.admin.playground.support.PlaygroundUrlResolver;
import com.zestflow.admin.playground.model.dto.PlaygroundSceneCreateDTO;
import com.zestflow.admin.playground.model.dto.PlaygroundSceneUpdateDTO;
import com.zestflow.admin.playground.model.vo.PlaygroundSceneVO;
import com.zestflow.admin.playground.service.PlaygroundSceneService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlaygroundSceneControllerTest {

    @Mock private PlaygroundSceneService sceneService;
    @Mock private ExecutorProxyService executorProxyService;
    @Mock private PlaygroundAccessControl accessControl;
    @Mock private PlaygroundUrlResolver playgroundUrlResolver;
    private PlaygroundSceneController controller;

    @BeforeEach
    void setUp() {
        controller = new PlaygroundSceneController(sceneService, executorProxyService, accessControl, playgroundUrlResolver);
        when(sceneService.getDefaultAppCode()).thenReturn("playground-app");
        when(playgroundUrlResolver.toDisplayUrl(eq("demo-app"), anyString())).thenAnswer(inv -> inv.getArgument(1));
    }

    // ==================== create ====================

    @Test
    void create_shouldReturnSuccessWithVO() {
        PlaygroundSceneVO vo = createTestVO();
        when(sceneService.create(any())).thenReturn(vo);

        PlaygroundSceneCreateDTO dto = new PlaygroundSceneCreateDTO();
        dto.setName("新场景");
        dto.setRequestPath("/execute");
        dto.setChainCode("CHN_TEST");

        var result = controller.create(dto);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).isNotNull();
        assertThat(result.getData().getSceneCode()).isEqualTo("SCN20260531000001");
    }

    // ==================== update ====================

    @Test
    void update_shouldReturnSuccess_whenExists() {
        PlaygroundSceneVO vo = createTestVO();
        when(sceneService.getById(1L)).thenReturn(vo);
        when(sceneService.update(eq(1L), any())).thenReturn(vo);

        var result = controller.update(1L, new PlaygroundSceneUpdateDTO());

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).isNotNull();
    }

    @Test
    void update_shouldReturn404_whenNotExists() {
        when(sceneService.getById(999L)).thenReturn(null);

        var result = controller.update(999L, new PlaygroundSceneUpdateDTO());

        assertThat(result.getCode()).isEqualTo(404);
    }

    // ==================== delete ====================

    @Test
    void delete_shouldReturnSuccess() {
        when(sceneService.getById(1L)).thenReturn(createTestVO());
        var result = controller.delete(1L);

        assertThat(result.getCode()).isEqualTo(200);
        verify(sceneService).delete(1L);
    }

    // ==================== getById ====================

    @Test
    void getById_shouldReturnVO_whenExists() {
        PlaygroundSceneVO vo = createTestVO();
        when(sceneService.getById(1L)).thenReturn(vo);

        var result = controller.getById(1L);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData().getSceneCode()).isEqualTo("SCN20260531000001");
    }

    @Test
    void getById_shouldReturn404_whenNotExists() {
        when(sceneService.getById(999L)).thenReturn(null);

        var result = controller.getById(999L);

        assertThat(result.getCode()).isEqualTo(404);
    }

    // ==================== getByCode ====================

    @Test
    void getByCode_shouldReturnVO_whenFound() {
        PlaygroundSceneVO vo = createTestVO();
        when(sceneService.getByCode("SCN001")).thenReturn(vo);

        var result = controller.getByCode("SCN001");

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).isNotNull();
    }

    @Test
    void getByCode_shouldReturn404_whenNotFound() {
        when(sceneService.getByCode("UNKNOWN")).thenReturn(null);

        var result = controller.getByCode("UNKNOWN");

        assertThat(result.getCode()).isEqualTo(404);
    }

    // ==================== queryPage ====================

    @Test
    void queryPage_shouldReturnPagedResults() {
        var result = controller.queryPage(null, null, 1, 20);

        assertThat(result.getCode()).isEqualTo(200);
        verify(sceneService).queryPage(null, null, 1, 20);
    }

    @Test
    void queryPage_shouldSearchByKeyword() {
        var result = controller.queryPage("test", null, 1, 10);

        assertThat(result.getCode()).isEqualTo(200);
        verify(sceneService).queryPage("test", null, 1, 10);
    }

    // ==================== listAll ====================

    @Test
    void listAll_shouldReturnList() {
        var result = controller.listAll(null);

        assertThat(result.getCode()).isEqualTo(200);
        verify(sceneService).listAll(null);
    }

    // ==================== available-endpoints ====================

    @Test
    void getAvailableEndpoints_shouldReturnRecords_whenExecutorHasEndpoints() {
        String json = """
                [{"className":"OrderController","methodName":"createOrder","requestPath":"/api/orders/create",\
                "requestMethod":"POST","parameters":[],"hasRequestBody":true,"requestBodyType":"OrderRequest",\
                "requestBodyTemplate":"{}","responseBodyType":"OrderResponse","responseBodyTemplate":"{}",\
                "requestHeaders":""}]\
                """;
        when(executorProxyService.getArrayFromExecutor(eq("demo-app"), eq("/api/endpoints"), isNull()))
                .thenReturn(json);

        var result = controller.getAvailableEndpoints("demo-app", null, null, 1, 10);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData().get("total")).isEqualTo(1);
        assertThat(result.getData().get("records")).asList().hasSize(1);
    }

    private PlaygroundSceneVO createTestVO() {
        PlaygroundSceneVO vo = new PlaygroundSceneVO();
        vo.setId(1L);
        vo.setSceneCode("SCN20260531000001");
        vo.setName("测试场景");
        vo.setRequestPath("/execute");
        vo.setRequestMethod("POST");
        vo.setBodyType("JSON");
        vo.setChainCode("CHN_TEST");
        vo.setRateLimit(30);
        vo.setAppCode("playground-app");
        vo.setCreatedBy("admin");
        vo.setUpdatedBy("admin");
        vo.setCreatedAt(LocalDateTime.now());
        vo.setUpdatedAt(LocalDateTime.now());
        return vo;
    }
}
