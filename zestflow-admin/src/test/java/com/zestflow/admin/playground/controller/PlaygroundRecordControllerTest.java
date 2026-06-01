package com.zestflow.admin.playground.controller;

import com.zestflow.admin.playground.model.dto.PlaygroundRecordQueryDTO;
import com.zestflow.admin.playground.model.vo.PlaygroundRecordVO;
import com.zestflow.admin.playground.service.PlaygroundRecordService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlaygroundRecordControllerTest {

    @Mock private PlaygroundRecordService recordService;
    private PlaygroundRecordController controller;

    @BeforeEach
    void setUp() {
        controller = new PlaygroundRecordController(recordService);
    }

    @Test
    void queryPage_shouldReturnSuccess() {
        PlaygroundRecordQueryDTO dto = new PlaygroundRecordQueryDTO();
        dto.setPage(1);
        dto.setSize(20);

        var result = controller.queryPage(dto);

        assertThat(result.getCode()).isEqualTo(200);
        verify(recordService).queryPage(dto);
    }

    @Test
    void queryPage_shouldPassFilters() {
        PlaygroundRecordQueryDTO dto = new PlaygroundRecordQueryDTO();
        dto.setSceneId(1L);
        dto.setStatus(1);
        dto.setSceneCode("SCN001");

        controller.queryPage(dto);

        verify(recordService).queryPage(argThat(q ->
                q.getSceneId().equals(1L) && q.getStatus() == 1 && "SCN001".equals(q.getSceneCode())));
    }

    @Test
    void getById_shouldReturnVO_whenExists() {
        PlaygroundRecordVO vo = createTestVO(1L);
        when(recordService.getById(1L)).thenReturn(vo);

        var result = controller.getById(1L);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData().getId()).isEqualTo(1L);
    }

    @Test
    void getById_shouldReturn404_whenNotExists() {
        when(recordService.getById(999L)).thenReturn(null);

        var result = controller.getById(999L);

        assertThat(result.getCode()).isEqualTo(404);
    }

    private PlaygroundRecordVO createTestVO(Long id) {
        PlaygroundRecordVO vo = new PlaygroundRecordVO();
        vo.setId(id);
        vo.setSceneCode("SCN001");
        vo.setSceneName("测试");
        vo.setChainCode("CHN_TEST");
        vo.setStatus(1);
        vo.setCostMs(50L);
        vo.setCreatedAt(LocalDateTime.now());
        return vo;
    }
}
