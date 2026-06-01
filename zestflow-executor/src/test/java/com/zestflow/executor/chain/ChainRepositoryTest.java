package com.zestflow.executor.chain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChainRepositoryTest {

    @Mock
    private JdbcTemplate jdbc;

    private ChainRepository repo;

    @Captor
    private ArgumentCaptor<String> sqlCaptor;

    @Captor
    private ArgumentCaptor<Object[]> argsCaptor;

    @BeforeEach
    void setUp() {
        repo = new ChainRepository(jdbc, 1L);
    }

    // ==================== incrementVersion ====================

    @Test
    void incrementVersionIncrementsAndReturnsNewVersion() {
        ChainPO updated = ChainPO.builder().code("CHN001").version(3).build();
        when(jdbc.update(anyString(), anyString(), anyString(), anyLong())).thenReturn(1);
        when(jdbc.query(anyString(), any(RowMapper.class), anyString(), anyLong()))
                .thenReturn(List.of(updated));

        int newVersion = repo.incrementVersion("CHN001");

        assertThat(newVersion).isEqualTo(3);
        verify(jdbc).update(sqlCaptor.capture(), anyString(), anyString(), anyLong());
        assertThat(sqlCaptor.getValue()).contains("version = version + 1");
    }

    @Test
    void incrementVersionNonExistentChainReturnsOne() {
        when(jdbc.update(anyString(), anyString(), anyString(), anyLong())).thenReturn(0);
        when(jdbc.query(anyString(), any(RowMapper.class), anyString(), anyLong()))
                .thenReturn(List.of());

        int newVersion = repo.incrementVersion("NON_EXISTENT");

        assertThat(newVersion).isEqualTo(1);
    }

    // ==================== saveVersionSnapshot ====================

    @Test
    void saveVersionSnapshotInsertsRecord() {
        repo.saveVersionSnapshot("CHN001", 2, "DSN001", "{}", "[]", "admin");

        verify(jdbc).update(sqlCaptor.capture(), argsCaptor.capture());
        assertThat(sqlCaptor.getValue()).contains("INSERT INTO zf_chain_version");
        Object[] args = argsCaptor.getValue();
        assertThat(args[0]).isEqualTo("CHN001");
        assertThat(args[1]).isEqualTo(2);
        assertThat(args[2]).isEqualTo("DSN001");
        assertThat(args[3]).isEqualTo("{}");
        assertThat(args[4]).isEqualTo("[]");
        assertThat(args[7]).isEqualTo("admin");
    }

    // ==================== listVersionSnapshots ====================

    @Test
    void listVersionSnapshotsReturnsOrderedList() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("id")).thenReturn(2L, 1L);
        when(rs.getString("chain_code")).thenReturn("CHN001", "CHN001");
        when(rs.getInt("version")).thenReturn(2, 1);
        when(rs.getString("design_code")).thenReturn("DSN001", "DSN001");
        when(rs.getString("graph_data")).thenReturn("{}", "{}");
        when(rs.getString("chain_data")).thenReturn("[]", "[]");
        when(rs.getString("created_by")).thenReturn("admin", "admin");
        when(rs.getString("created_at")).thenReturn("2026-05-31 12:00:00", "2026-05-30 12:00:00");

        when(jdbc.query(anyString(), any(RowMapper.class), anyString(), anyLong()))
                .thenAnswer(invocation -> {
                    RowMapper<ChainVersionPO> mapper = invocation.getArgument(1);
                    ChainVersionPO v1 = mapper.mapRow(rs, 0);
                    ChainVersionPO v2 = mapper.mapRow(rs, 1);
                    return java.util.List.of(v1, v2);
                });

        List<ChainVersionPO> result = repo.listVersionSnapshots("CHN001");
        assertThat(result).hasSize(2);
    }

    @Test
    void listVersionSnapshotsEmpty() {
        when(jdbc.query(anyString(), any(RowMapper.class), anyString(), anyLong()))
                .thenReturn(List.of());

        List<ChainVersionPO> result = repo.listVersionSnapshots("CHN001");

        assertThat(result).isEmpty();
    }

    // ==================== getVersionSnapshot ====================

    @Test
    void getVersionSnapshotFound() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("id")).thenReturn(1L);
        when(rs.getString("chain_code")).thenReturn("CHN001");
        when(rs.getInt("version")).thenReturn(2);

        when(jdbc.query(anyString(), any(RowMapper.class), anyString(), anyInt(), anyLong()))
                .thenAnswer(invocation -> {
                    RowMapper<ChainVersionPO> mapper = invocation.getArgument(1);
                    return java.util.List.of(mapper.mapRow(rs, 0));
                });

        ChainVersionPO result = repo.getVersionSnapshot("CHN001", 2);

        assertThat(result).isNotNull();
        assertThat(result.getChainCode()).isEqualTo("CHN001");
        assertThat(result.getVersion()).isEqualTo(2);
    }

    @Test
    void getVersionSnapshotNotFound() {
        when(jdbc.query(anyString(), any(RowMapper.class), anyString(), anyInt(), anyLong()))
                .thenReturn(List.of());

        ChainVersionPO result = repo.getVersionSnapshot("CHN001", 99);

        assertThat(result).isNull();
    }

    // ==================== rollbackToVersion ====================

    @Test
    void rollbackToVersionUpdatesBindingAndRestoresDesign() {
        ChainPO existing = ChainPO.builder()
                .code("CHN001").name("test").status(4).designCode("DSN001")
                .version(3).updatedBy("admin").build();

        // Mock getVersionSnapshot — use spy on real object
        ChainVersionPO snapshot = ChainVersionPO.builder()
                .id(1L).chainCode("CHN001").version(2)
                .designCode("DSN_OLD").graphData("{\"old\":true}").chainData("[\"old\"]")
                .createdBy("admin").build();

        ChainRepository spy = spy(repo);
        doReturn(snapshot).when(spy).getVersionSnapshot("CHN001", 2);
        doReturn(existing).when(spy).get("CHN001");

        spy.rollbackToVersion("CHN001", 2, "sys");

        // Verify old binding deleted
        verify(jdbc).update(
                argThat(sql -> sql.contains("DELETE FROM zf_design_binding")),
                eq("CHN001"));

        // Verify new binding inserted
        verify(jdbc).update(
                argThat(sql -> sql.contains("INSERT INTO zf_design_binding")),
                eq("DSN_OLD"), eq("CHN001"));

        // Verify chain status reset to 2 (no design_code set)
        verify(jdbc).update(
                argThat(sql -> sql.contains("UPDATE zf_chain") && sql.contains("status = 2")),
                eq("sys"), anyString(), eq("CHN001"), eq(1L));

        // Verify design restore
        verify(jdbc).update(
                argThat(sql -> sql.contains("UPDATE zf_design")),
                eq("{\"old\":true}"), eq("[\"old\"]"), eq("sys"), anyString(), eq("DSN_OLD"), eq(1L));
    }

    @Test
    void rollbackToVersionSnapshotNotFoundReturnsNull() {
        ChainRepository spy = spy(repo);
        doReturn(null).when(spy).getVersionSnapshot("CHN001", 99);

        ChainPO result = spy.rollbackToVersion("CHN001", 99, "admin");

        assertThat(result).isNull();
        verify(jdbc, never()).update(anyString(), any(), any());
    }

    @Test
    void rollbackToVersionChainNotFoundReturnsNull() {
        ChainVersionPO snapshot = ChainVersionPO.builder()
                .chainCode("CHN001").version(2).build();

        ChainRepository spy = spy(repo);
        doReturn(snapshot).when(spy).getVersionSnapshot("CHN001", 2);
        doReturn(null).when(spy).get("CHN001");

        ChainPO result = spy.rollbackToVersion("CHN001", 2, "admin");

        assertThat(result).isNull();
        verify(jdbc, never()).update(anyString(), any(), any());
    }

    // ==================== ROW_MAPPER reads version field ====================

    @Test
    void rowMapperReadsVersionWhenPresent() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("code")).thenReturn("CHN001");
        when(rs.getString("name")).thenReturn("test");
        when(rs.getString("description")).thenReturn("desc");
        when(rs.getInt("status")).thenReturn(4);
        when(rs.getString("design_code")).thenReturn("DSN001");
        when(rs.getInt("version")).thenReturn(5);
        when(rs.getString("created_by")).thenReturn("admin");
        when(rs.getString("updated_by")).thenReturn("admin");
        when(rs.getString("created_at")).thenReturn("2026-05-31 12:00:00");
        when(rs.getString("updated_at")).thenReturn("2026-05-31 13:00:00");
        when(rs.getInt("is_deleted")).thenReturn(0);

        // Use the actual ROW_MAPPER from ChainRepository via reflection
        java.lang.reflect.Field mapperField = ChainRepository.class.getDeclaredField("ROW_MAPPER");
        mapperField.setAccessible(true);
        @SuppressWarnings("unchecked")
        RowMapper<ChainPO> rowMapper = (RowMapper<ChainPO>) mapperField.get(null);

        ChainPO result = rowMapper.mapRow(rs, 0);

        assertThat(result.getCode()).isEqualTo("CHN001");
        assertThat(result.getVersion()).isEqualTo(5);
    }

    @Test
    void rowMapperHandlesMissingVersionGracefully() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("code")).thenReturn("CHN001");
        when(rs.getString("name")).thenReturn("test");
        when(rs.getString("description")).thenReturn("desc");
        when(rs.getInt("status")).thenReturn(4);
        when(rs.getString("design_code")).thenReturn("DSN001");
        when(rs.getInt("version")).thenThrow(new RuntimeException("Column not found"));
        when(rs.getString("created_by")).thenReturn("admin");
        when(rs.getString("updated_by")).thenReturn("admin");
        when(rs.getString("created_at")).thenReturn("2026-05-31 12:00:00");
        when(rs.getString("updated_at")).thenReturn("2026-05-31 13:00:00");
        when(rs.getInt("is_deleted")).thenReturn(0);

        java.lang.reflect.Field mapperField = ChainRepository.class.getDeclaredField("ROW_MAPPER");
        mapperField.setAccessible(true);
        @SuppressWarnings("unchecked")
        RowMapper<ChainPO> rowMapper = (RowMapper<ChainPO>) mapperField.get(null);

        ChainPO result = rowMapper.mapRow(rs, 0);

        assertThat(result.getCode()).isEqualTo("CHN001");
        assertThat(result.getVersion()).isNull();
    }
}
