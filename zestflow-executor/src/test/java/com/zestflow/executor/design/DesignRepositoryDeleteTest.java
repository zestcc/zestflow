package com.zestflow.executor.design;

import com.zestflow.executor.chain.ChainPO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DesignRepositoryDeleteTest {

    @Mock
    private JdbcTemplate jdbc;

    @Captor
    private ArgumentCaptor<String> sqlCaptor;

    private DesignRepository repo;

    @BeforeEach
    void setUp() {
        repo = new DesignRepository(jdbc, 1L);
    }

    @Test
    void delete_cascadesSoftDeleteToBoundChainsAndRemovesBindings() {
        DesignPO design = DesignPO.builder().code("DSN001").name("test").updatedBy("admin").build();
        ChainPO chain = ChainPO.builder().code("CHN001").build();

        when(jdbc.query(contains("FROM zf_design WHERE code = ?"), any(RowMapper.class), eq("DSN001"), eq(1L)))
                .thenReturn(List.of(design));
        when(jdbc.query(contains("zf_design_binding"), any(RowMapper.class), eq("DSN001"), eq(1L)))
                .thenReturn(List.of(chain));
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        DesignPO removed = repo.delete("DSN001", "admin");

        assertThat(removed).isNotNull();
        assertThat(removed.getCode()).isEqualTo("DSN001");

        verify(jdbc, atLeastOnce()).update(sqlCaptor.capture(), any(Object[].class));
        assertThat(sqlCaptor.getAllValues().stream().anyMatch(sql -> sql.contains("zf_chain") && sql.contains("is_deleted=1")))
                .isTrue();
        verify(jdbc).update(contains("DELETE FROM zf_design_binding"), eq("DSN001"), eq(1L));
        verify(jdbc).update(contains("UPDATE zf_design SET is_deleted=1"), any(), any(), eq("DSN001"), eq(1L));
    }

    @Test
    void delete_returnsNullWhenDesignNotFound() {
        when(jdbc.query(contains("FROM zf_design WHERE code = ?"), any(RowMapper.class), eq("DSN404"), eq(1L)))
                .thenReturn(List.of());

        assertThat(repo.delete("DSN404", "admin")).isNull();
        verify(jdbc, never()).update(contains("DELETE FROM zf_design_binding"), any(), any());
    }
}
