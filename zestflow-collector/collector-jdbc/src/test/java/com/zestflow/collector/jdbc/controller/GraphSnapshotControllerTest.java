package com.zestflow.collector.jdbc.controller;

import com.zestflow.collector.jdbc.config.CollectorProperties;
import com.zestflow.collector.jdbc.service.ChainGraphSnapshotService;
import com.zestflow.common.model.dto.ChainSnapshotDTO;
import com.zestflow.common.model.dto.ChainSnapshotSyncDTO;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * GraphSnapshotController 单元测试
 */
@ExtendWith(MockitoExtension.class)
class GraphSnapshotControllerTest {

    @Mock private ChainGraphSnapshotService snapshotService;
    @Mock private CollectorProperties properties;
    @Mock private HttpServletRequest request;

    private GraphSnapshotController controller;

    @BeforeEach
    void setUp() {
        controller = new GraphSnapshotController(snapshotService, properties);
    }

    // ==================== POST /collector/snapshots ====================

    @Test
    void syncSnapshot_success_returnsVersion() {
        when(properties.getAccessToken()).thenReturn(null); // 无 token 不校验
        ChainSnapshotSyncDTO dto = new ChainSnapshotSyncDTO();
        dto.setChainCode("chain-1");
        dto.setGraphData("{}");
        dto.setAppCode("app-a");
        dto.setCreatedBy("admin");
        when(snapshotService.syncSnapshot("chain-1", "{}", "app-a", "admin")).thenReturn(3);

        var result = controller.syncSnapshot(dto, request);

        assertThat(result.getCode()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        Map<String, Integer> data = (Map<String, Integer>) result.getData();
        assertThat(data.get("version")).isEqualTo(3);
    }

    @SuppressWarnings("unchecked")
    @Test
    void syncSnapshot_missingChainCode_returns400() {
        when(properties.getAccessToken()).thenReturn(null);
        ChainSnapshotSyncDTO dto = new ChainSnapshotSyncDTO();
        dto.setChainCode(""); // empty chainCode

        var result = controller.syncSnapshot(dto, request);

        assertThat(result.getCode()).isEqualTo(400);
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertThat(data).isNull(); // 没有 data
        verify(snapshotService, never()).syncSnapshot(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void syncSnapshot_invalidToken_returns401() {
        when(properties.getAccessToken()).thenReturn("secret");
        when(request.getHeader("X-Collector-Token")).thenReturn("wrong");

        ChainSnapshotSyncDTO dto = new ChainSnapshotSyncDTO();
        dto.setChainCode("chain-1");

        var result = controller.syncSnapshot(dto, request);

        assertThat(result.getCode()).isEqualTo(401);
        verify(snapshotService, never()).syncSnapshot(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void syncSnapshot_validToken_passes() {
        when(properties.getAccessToken()).thenReturn("secret");
        when(request.getHeader("X-Collector-Token")).thenReturn("secret");

        ChainSnapshotSyncDTO dto = new ChainSnapshotSyncDTO();
        dto.setChainCode("chain-1");
        dto.setGraphData("{}");
        when(snapshotService.syncSnapshot("chain-1", "{}", null, null)).thenReturn(1);

        var result = controller.syncSnapshot(dto, request);

        assertThat(result.getCode()).isEqualTo(200);
    }

    // ==================== GET /collector/snapshots ====================

    @Test
    void getSnapshot_found_returnsDTO() {
        when(properties.getAccessToken()).thenReturn(null);
        ChainSnapshotDTO dto = ChainSnapshotDTO.builder()
                .chainCode("chain-1").version(2).graphData("{}")
                .status(1).appCode("app-a").build();
        when(snapshotService.findSnapshotAt("chain-1", 1000L)).thenReturn(dto);

        var result = controller.getSnapshot("chain-1", 1000L, request);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(((ChainSnapshotDTO) result.getData()).getChainCode()).isEqualTo("chain-1");
        assertThat(((ChainSnapshotDTO) result.getData()).getVersion()).isEqualTo(2);
    }

    @Test
    void getSnapshot_notFound_returns404() {
        when(properties.getAccessToken()).thenReturn(null);
        when(snapshotService.findSnapshotAt("chain-1", 1000L)).thenReturn(null);

        var result = controller.getSnapshot("chain-1", 1000L, request);

        assertThat(result.getCode()).isEqualTo(404);
    }

    @Test
    void getSnapshot_invalidToken_returns401() {
        when(properties.getAccessToken()).thenReturn("secret");
        when(request.getHeader("X-Collector-Token")).thenReturn("wrong");

        var result = controller.getSnapshot("chain-1", 1000L, request);

        assertThat(result.getCode()).isEqualTo(401);
        verify(snapshotService, never()).findSnapshotAt(anyString(), anyLong());
    }

    @Test
    void getSnapshot_emptyTokenBypass() {
        when(properties.getAccessToken()).thenReturn("");
        ChainSnapshotDTO dto = ChainSnapshotDTO.builder()
                .chainCode("chain-1").version(1).graphData("{}").build();
        when(snapshotService.findSnapshotAt("chain-1", 1000L)).thenReturn(dto);

        var result = controller.getSnapshot("chain-1", 1000L, request);

        assertThat(result.getCode()).isEqualTo(200);
        verify(request, never()).getHeader(anyString()); // 空 token 不校验
    }
}
