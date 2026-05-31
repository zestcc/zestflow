package com.zestflow.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zestflow.admin.constant.ErrorCode;
import com.zestflow.admin.model.entity.ExecutorRegistryPO;
import com.zestflow.admin.model.vo.ExecutorRegistryVO;
import com.zestflow.admin.repository.ExecutorRegistryMapper;
import com.zestflow.admin.service.TenantAppContext;
import com.zestflow.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExecutorRegistryServiceImplTest {

    @Mock private ExecutorRegistryMapper executorRegistryMapper;
    @Mock private TenantAppContext tenantAppContext;

    private ExecutorRegistryServiceImpl executorRegistryService;

    @BeforeEach
    void setUp() {
        executorRegistryService = new ExecutorRegistryServiceImpl(executorRegistryMapper, tenantAppContext);
    }

    @Test
    void updateStatus_success() {
        ExecutorRegistryPO exec = new ExecutorRegistryPO();
        exec.setId(10L);
        exec.setExecutorId("executor-1");
        exec.setStatus(1);
        when(executorRegistryMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(exec);

        executorRegistryService.updateStatus("executor-1", 0);

        verify(executorRegistryMapper).updateById(exec);
        assertThat(exec.getStatus()).isEqualTo(0);
    }

    @Test
    void updateStatus_notFound_throws() {
        when(executorRegistryMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> executorRegistryService.updateStatus("unknown", 0))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EXECUTOR_NOT_FOUND);
    }

    @Test
    void updateStatus_invalidStatus_throws() {
        ExecutorRegistryPO exec = new ExecutorRegistryPO();
        exec.setExecutorId("e1");
        when(executorRegistryMapper.selectOne(any())).thenReturn(exec);

        assertThatThrownBy(() -> executorRegistryService.updateStatus("e1", 999))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_ERROR);
    }

    // ==================== listAll（含 app_code 过滤） ====================

    @Test
    void listAll_superAdmin_noFilter() {
        when(tenantAppContext.getCurrentUserAppCodes()).thenReturn(Collections.emptySet());
        ExecutorRegistryPO po = createPo("e1", "app-a");
        when(executorRegistryMapper.selectList(any())).thenReturn(List.of(po));

        List<ExecutorRegistryVO> list = executorRegistryService.listAll();

        assertThat(list).hasSize(1);
        assertThat(list.get(0).getExecutorId()).isEqualTo("e1");
        // 应不过滤 app_code
        verify(executorRegistryMapper).selectList(any());
    }

    @Test
    void listAll_normalUser_filtersByAppCode() {
        when(tenantAppContext.getCurrentUserAppCodes()).thenReturn(Set.of("app-a", "app-b"));
        ExecutorRegistryPO po = createPo("e1", "app-a");
        when(executorRegistryMapper.selectList(any())).thenReturn(List.of(po));

        List<ExecutorRegistryVO> list = executorRegistryService.listAll();

        assertThat(list).hasSize(1);
        verify(executorRegistryMapper).selectList(any());
    }

    @Test
    void listAll_normalUserNoApps_returnsEmpty() {
        when(tenantAppContext.getCurrentUserAppCodes()).thenReturn(Collections.emptySet());
        when(executorRegistryMapper.selectList(any())).thenReturn(Collections.emptyList());

        List<ExecutorRegistryVO> list = executorRegistryService.listAll();

        assertThat(list).isEmpty();
    }

    // ==================== getByExecutorId ====================

    @Test
    void getByExecutorId_found_returnsVO() {
        ExecutorRegistryPO po = createPo("e1", "app-a");
        when(executorRegistryMapper.selectOne(any())).thenReturn(po);

        ExecutorRegistryVO vo = executorRegistryService.getByExecutorId("e1");

        assertThat(vo.getExecutorId()).isEqualTo("e1");
        assertThat(vo.getAppCode()).isEqualTo("app-a");
    }

    @Test
    void getByExecutorId_notFound_throws() {
        when(executorRegistryMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> executorRegistryService.getByExecutorId("unknown"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EXECUTOR_NOT_FOUND);
    }

    // ==================== listDistinctApps ====================

    @Test
    void listDistinctApps_returnsAppCodeAndName() {
        ExecutorRegistryPO po = createPo("e1", "app-a");
        po.setAppName("应用A");
        when(executorRegistryMapper.selectList(any())).thenReturn(List.of(po));

        List<java.util.Map<String, String>> apps = executorRegistryService.listDistinctApps();

        assertThat(apps).hasSize(1);
        assertThat(apps.get(0).get("appCode")).isEqualTo("app-a");
        assertThat(apps.get(0).get("appName")).isEqualTo("应用A");
    }

    @Test
    void listDistinctApps_nullAppName_fallsBackToAppCode() {
        ExecutorRegistryPO po = createPo("e1", "app-a");
        po.setAppName(null);
        when(executorRegistryMapper.selectList(any())).thenReturn(List.of(po));

        List<java.util.Map<String, String>> apps = executorRegistryService.listDistinctApps();

        assertThat(apps.get(0).get("appName")).isEqualTo("app-a");
    }

    private ExecutorRegistryPO createPo(String id, String appCode) {
        ExecutorRegistryPO po = new ExecutorRegistryPO();
        po.setId(1L);
        po.setExecutorId(id);
        po.setAppCode(appCode);
        po.setExecutorHost("192.168.1.1");
        po.setExecutorPort(9999);
        po.setStatus(1);
        po.setLastHeartbeat(LocalDateTime.now());
        return po;
    }
}
