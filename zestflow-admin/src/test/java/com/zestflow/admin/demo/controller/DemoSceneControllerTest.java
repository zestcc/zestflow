package com.zestflow.admin.demo.controller;

import com.zestflow.admin.demo.model.dto.DemoSceneCreateDTO;
import com.zestflow.admin.demo.model.dto.DemoSceneUpdateDTO;
import com.zestflow.admin.demo.model.vo.DemoSceneVO;
import com.zestflow.admin.demo.service.DemoSceneService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DemoSceneControllerTest {

    @Mock private DemoSceneService sceneService;
    private DemoSceneController controller;

    @BeforeEach
    void setUp() {
        controller = new DemoSceneController(sceneService);
    }

    // ==================== create ====================

    @Test
    void create_shouldReturnSuccessWithVO() {
        DemoSceneVO vo = createTestVO();
        when(sceneService.create(any())).thenReturn(vo);

        DemoSceneCreateDTO dto = new DemoSceneCreateDTO();
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
        DemoSceneVO vo = createTestVO();
        when(sceneService.update(eq(1L), any())).thenReturn(vo);

        var result = controller.update(1L, new DemoSceneUpdateDTO());

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).isNotNull();
    }

    @Test
    void update_shouldReturn404_whenNotExists() {
        when(sceneService.update(eq(999L), any())).thenReturn(null);

        var result = controller.update(999L, new DemoSceneUpdateDTO());

        assertThat(result.getCode()).isEqualTo(404);
    }

    // ==================== delete ====================

    @Test
    void delete_shouldReturnSuccess() {
        var result = controller.delete(1L);

        assertThat(result.getCode()).isEqualTo(200);
        verify(sceneService).delete(1L);
    }

    // ==================== getById ====================

    @Test
    void getById_shouldReturnVO_whenExists() {
        DemoSceneVO vo = createTestVO();
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
        DemoSceneVO vo = createTestVO();
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
        var result = controller.queryPage(null, 1, 20);

        assertThat(result.getCode()).isEqualTo(200);
        verify(sceneService).queryPage(null, 1, 20);
    }

    @Test
    void queryPage_shouldSearchByKeyword() {
        var result = controller.queryPage("test", 1, 10);

        assertThat(result.getCode()).isEqualTo(200);
        verify(sceneService).queryPage("test", 1, 10);
    }

    // ==================== listAll ====================

    @Test
    void listAll_shouldReturnList() {
        var result = controller.listAll();

        assertThat(result.getCode()).isEqualTo(200);
        verify(sceneService).listAll();
    }

    private DemoSceneVO createTestVO() {
        DemoSceneVO vo = new DemoSceneVO();
        vo.setId(1L);
        vo.setSceneCode("SCN20260531000001");
        vo.setName("测试场景");
        vo.setRequestPath("/execute");
        vo.setRequestMethod("POST");
        vo.setBodyType("JSON");
        vo.setChainCode("CHN_TEST");
        vo.setRateLimit(30);
        vo.setAppCode("demo-app");
        vo.setCreatedBy("admin");
        vo.setUpdatedBy("admin");
        vo.setCreatedAt(LocalDateTime.now());
        vo.setUpdatedAt(LocalDateTime.now());
        return vo;
    }
}
