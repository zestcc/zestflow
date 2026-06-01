package com.zestflow.collector.jdbc.service;

import com.zestflow.collector.jdbc.entity.ChainGraphSnapshotPO;
import com.zestflow.collector.jdbc.mapper.ChainEventMapper;
import com.zestflow.collector.jdbc.mapper.ChainGraphSnapshotMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ChainGraphSnapshotService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class ChainGraphSnapshotServiceTest {

    @Mock private ChainGraphSnapshotMapper snapshotMapper;
    @Mock private ChainEventMapper chainEventMapper;
    @Captor private ArgumentCaptor<ChainGraphSnapshotPO> poCaptor;

    private ChainGraphSnapshotService snapshotService;

    @BeforeEach
    void setUp() {
        snapshotService = new ChainGraphSnapshotService(snapshotMapper, chainEventMapper);
    }

    // ==================== syncSnapshot ====================

    @Test
    void syncSnapshot_firstVersion_returns1() {
        when(snapshotMapper.selectMaxVersionForUpdate("chain-1", null)).thenReturn(null);
        when(snapshotMapper.deprecateUnreferenced("chain-1", null)).thenReturn(0);

        int version = snapshotService.syncSnapshot("chain-1", "{}", "app-a", null, "admin");

        assertThat(version).isEqualTo(1);
        verify(snapshotMapper).insert(poCaptor.capture());
        ChainGraphSnapshotPO inserted = poCaptor.getValue();
        assertThat(inserted.getChainCode()).isEqualTo("chain-1");
        assertThat(inserted.getVersion()).isEqualTo(1);
        assertThat(inserted.getGraphData()).isEqualTo("{}");
        assertThat(inserted.getStatus()).isEqualTo(1);
        assertThat(inserted.getAppCode()).isEqualTo("app-a");
        assertThat(inserted.getCreatedBy()).isEqualTo("admin");
    }

    @Test
    void syncSnapshot_versionIncrement() {
        when(snapshotMapper.selectMaxVersionForUpdate("chain-1", null)).thenReturn(3);
        when(snapshotMapper.deprecateUnreferenced("chain-1", null)).thenReturn(1);

        int version = snapshotService.syncSnapshot("chain-1", "{\"nodes\":[]}", "app-a", null, "admin");

        assertThat(version).isEqualTo(4);
        verify(snapshotMapper).insert(poCaptor.capture());
        assertThat(poCaptor.getValue().getVersion()).isEqualTo(4);
    }

    @Test
    void syncSnapshot_callsDeprecateBeforeInsert() {
        when(snapshotMapper.selectMaxVersionForUpdate("chain-1", null)).thenReturn(2);
        when(snapshotMapper.deprecateUnreferenced("chain-1", null)).thenReturn(1);

        snapshotService.syncSnapshot("chain-1", "{}", "app-a", null, "admin");

        verify(snapshotMapper).deprecateUnreferenced("chain-1", null);
        verify(snapshotMapper).insert(any(ChainGraphSnapshotPO.class));
    }

    @Test
    void syncSnapshot_forUpdateCalledBeforeDeprecate() {
        when(snapshotMapper.selectMaxVersionForUpdate("chain-1", null)).thenReturn(2);
        when(snapshotMapper.deprecateUnreferenced("chain-1", null)).thenReturn(1);

        snapshotService.syncSnapshot("chain-1", "{}", "app-a", null, "admin");

        verify(snapshotMapper).selectMaxVersionForUpdate("chain-1", null);
    }

    // ==================== findSnapshotAt ====================

    @Test
    void findSnapshotAt_exactTimeMatch_returnsSnapshot() {
        String chainCode = "chain-1";
        long timestamp = 1717200000000L; // 2024-06-01T00:00:00 UTC
        // capture the actual LocalDateTime and return a mock PO
        LocalDateTime execTime = LocalDateTime.ofEpochSecond(
                timestamp / 1000, (int) ((timestamp % 1000) * 1_000_000),
                ZoneId.systemDefault().getRules().getOffset(java.time.Instant.now()));

        ChainGraphSnapshotPO po = ChainGraphSnapshotPO.builder()
                .chainCode(chainCode).version(2).graphData("{\"cells\":[]}")
                .status(1).appCode("app-a").createdBy("admin")
                .createdAt(execTime).build();

        when(snapshotMapper.selectOne(any())).thenReturn(po);

        var result = snapshotService.findSnapshotAt(chainCode, timestamp, null);

        assertThat(result).isNotNull();
        assertThat(result.getChainCode()).isEqualTo(chainCode);
        assertThat(result.getVersion()).isEqualTo(2);
        assertThat(result.getGraphData()).isEqualTo("{\"cells\":[]}");
    }

    @Test
    void findSnapshotAt_noTimeMatch_fallsbackToLatest() {
        String chainCode = "chain-1";
        long timestamp = 1717200000000L;

        // first query returns null (no time match)
        when(snapshotMapper.selectOne(any()))
                .thenReturn(null)  // first call: time-based query
                .thenReturn(       // second call: fallback latest
                        ChainGraphSnapshotPO.builder()
                                .chainCode(chainCode).version(5)
                                .graphData("{\"cells\":[]}").status(1)
                                .appCode("app-a").createdBy("admin")
                                .createdAt(LocalDateTime.now()).build());

        var result = snapshotService.findSnapshotAt(chainCode, timestamp, null);

        assertThat(result).isNotNull();
        assertThat(result.getVersion()).isEqualTo(5);
        verify(snapshotMapper, times(2)).selectOne(any());
    }

    @Test
    void findSnapshotAt_noSnapshots_returnsNull() {
        when(snapshotMapper.selectOne(any())).thenReturn(null);

        var result = snapshotService.findSnapshotAt("chain-1", 1717200000000L, null);

        assertThat(result).isNull();
        verify(snapshotMapper, times(2)).selectOne(any());
    }

    @Test
    void findSnapshotAt_toDTOCopiesAllFields() {
        LocalDateTime now = LocalDateTime.now();
        ChainGraphSnapshotPO po = ChainGraphSnapshotPO.builder()
                .chainCode("chain-1").version(3).graphData("{\"x\":1}")
                .status(1).tenantId(100L).appCode("app-a")
                .createdBy("admin").createdAt(now).build();

        when(snapshotMapper.selectOne(any())).thenReturn(po);

        var result = snapshotService.findSnapshotAt("chain-1", System.currentTimeMillis(), null);

        assertThat(result.getChainCode()).isEqualTo("chain-1");
        assertThat(result.getVersion()).isEqualTo(3);
        assertThat(result.getGraphData()).isEqualTo("{\"x\":1}");
        assertThat(result.getStatus()).isEqualTo(1);
        assertThat(result.getTenantId()).isEqualTo(100L);
        assertThat(result.getAppCode()).isEqualTo("app-a");
        assertThat(result.getCreatedBy()).isEqualTo("admin");
        assertThat(result.getCreatedAt()).isNotNull();
    }
}
