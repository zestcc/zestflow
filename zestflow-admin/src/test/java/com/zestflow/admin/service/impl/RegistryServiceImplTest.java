package com.zestflow.admin.service.impl;

import com.zestflow.admin.constant.ErrorCode;
import com.zestflow.admin.model.entity.ExecutorRegistryPO;
import com.zestflow.admin.repository.ExecutorRegistryMapper;
import com.zestflow.admin.service.DictTypeService;
import com.zestflow.common.constant.RegistryConstants;
import com.zestflow.common.exception.BizException;
import com.zestflow.common.model.dto.ComponentDTO;
import com.zestflow.common.model.dto.HeartbeatDTO;
import com.zestflow.common.model.dto.RegisterDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegistryServiceImplTest {

    @Mock private ExecutorRegistryMapper executorRegistryMapper;
    @Mock private DictTypeService dictTypeService;
    @Captor private ArgumentCaptor<ExecutorRegistryPO> registryCaptor;

    private RegistryServiceImpl registryService;

    @BeforeEach
    void setUp() {
        registryService = new RegistryServiceImpl(executorRegistryMapper, dictTypeService);
    }

    @Test
    void registerNewExecutor() {
        when(executorRegistryMapper.selectOne(any())).thenReturn(null);
        when(executorRegistryMapper.selectList(any())).thenReturn(Collections.emptyList());

        RegisterDTO dto = new RegisterDTO();
        dto.setExecutorId("executor-1");
        dto.setHost("192.168.1.1");
        dto.setPort(9999);
        dto.setAppCode("test-app");
        dto.setAppName("测试应用");

        registryService.register(dto, 1L);

        verify(executorRegistryMapper).insert(registryCaptor.capture());
        ExecutorRegistryPO inserted = registryCaptor.getValue();
        assertThat(inserted.getExecutorId()).isEqualTo("executor-1");
        assertThat(inserted.getExecutorHost()).isEqualTo("192.168.1.1");
        assertThat(inserted.getStatus()).isEqualTo(RegistryConstants.STATUS_ONLINE);
        assertThat(inserted.getAppCode()).isEqualTo("test-app");
        assertThat(inserted.getAppName()).isEqualTo("测试应用");
        assertThat(inserted.getTenantId()).isEqualTo(1L);
        // 应自动创建字典项
        verify(dictTypeService).ensureDictData("app_type", "test-app", "测试应用");
    }

    @Test
    void registerNewExecutor_withComponents_autoCreatesDictData() {
        when(executorRegistryMapper.selectOne(any())).thenReturn(null);
        when(executorRegistryMapper.selectList(any())).thenReturn(Collections.emptyList());

        RegisterDTO dto = new RegisterDTO();
        dto.setExecutorId("executor-2");
        dto.setHost("192.168.1.2");
        dto.setPort(9999);
        dto.setAppCode("test-app");
        dto.setComponents(List.of(
                ComponentDTO.builder().componentId("comp1").componentType("EXECUTOR").componentName("测试元件").build(),
                ComponentDTO.builder().componentId("comp2").componentType("PREDICATE").componentName("条件元件").build()
        ));

        registryService.register(dto, 1L);

        verify(dictTypeService, times(3)).ensureDictData(anyString(), anyString(), anyString());
        verify(dictTypeService).ensureDictData("app_type", "test-app", "test-app");
        verify(dictTypeService).ensureDictData("component_type", "EXECUTOR", "EXECUTOR");
        verify(dictTypeService).ensureDictData("component_type", "PREDICATE", "PREDICATE");
    }

    @Test
    void reRegisterExistingExecutor() {
        ExecutorRegistryPO existing = new ExecutorRegistryPO();
        existing.setId(1L);
        existing.setExecutorId("executor-1");
        existing.setAppCode("test-app");
        when(executorRegistryMapper.selectOne(any())).thenReturn(existing);

        RegisterDTO dto = new RegisterDTO();
        dto.setExecutorId("executor-1");
        dto.setHost("192.168.1.2");
        dto.setPort(9998);
        dto.setAppCode("test-app");

        registryService.register(dto, null);

        verify(executorRegistryMapper).updateById(registryCaptor.capture());
        ExecutorRegistryPO updated = registryCaptor.getValue();
        assertThat(updated.getExecutorHost()).isEqualTo("192.168.1.2");
        assertThat(updated.getExecutorPort()).isEqualTo(9998);
        assertThat(updated.getStatus()).isEqualTo(RegistryConstants.STATUS_ONLINE);
        verify(dictTypeService, never()).ensureDictData(anyString(), anyString(), anyString());
    }

    @Test
    void register_sameAddressDifferentExecutorId_mergesByAddress() {
        when(executorRegistryMapper.selectOne(any())).thenReturn(null);

        ExecutorRegistryPO oldRecord = new ExecutorRegistryPO();
        oldRecord.setId(1L);
        oldRecord.setExecutorId("demo-app@192.168.1.5:30550");
        oldRecord.setExecutorHost("127.0.0.1");
        oldRecord.setExecutorPort(30550);
        oldRecord.setAppCode("demo-app");
        when(executorRegistryMapper.selectList(any())).thenReturn(List.of(oldRecord));

        RegisterDTO dto = new RegisterDTO();
        dto.setExecutorId("demo-app@127.0.0.1:30550");
        dto.setHost("127.0.0.1");
        dto.setPort(30550);
        dto.setAppCode("demo-app");

        registryService.register(dto, 1L);

        verify(executorRegistryMapper, never()).insert(any(ExecutorRegistryPO.class));
        verify(executorRegistryMapper).updateById(registryCaptor.capture());
        ExecutorRegistryPO updated = registryCaptor.getValue();
        assertThat(updated.getExecutorId()).isEqualTo("demo-app@127.0.0.1:30550");
        assertThat(updated.getExecutorHost()).isEqualTo("127.0.0.1");
        assertThat(updated.getStatus()).isEqualTo(RegistryConstants.STATUS_ONLINE);
    }

    @Test
    void register_sameAddressDuplicateRows_mergesAndDeletesExtras() {
        when(executorRegistryMapper.selectOne(any())).thenReturn(null);

        ExecutorRegistryPO old1 = new ExecutorRegistryPO();
        old1.setId(1L);
        old1.setExecutorId("demo-app@192.168.1.5:30550");
        old1.setExecutorHost("127.0.0.1");
        old1.setExecutorPort(30550);
        old1.setAppCode("demo-app");
        ExecutorRegistryPO old2 = new ExecutorRegistryPO();
        old2.setId(2L);
        old2.setExecutorId("demo-app@127.0.0.1:30550");
        old2.setExecutorHost("127.0.0.1");
        old2.setExecutorPort(30550);
        old2.setAppCode("demo-app");
        when(executorRegistryMapper.selectList(any())).thenReturn(List.of(old1, old2));

        RegisterDTO dto = new RegisterDTO();
        dto.setExecutorId("demo-app@127.0.0.1:30550");
        dto.setHost("127.0.0.1");
        dto.setPort(30550);
        dto.setAppCode("demo-app");

        registryService.register(dto, 1L);

        verify(executorRegistryMapper).deleteById(2L);
        verify(executorRegistryMapper, times(1)).deleteById(anyLong());
        verify(executorRegistryMapper).updateById(registryCaptor.capture());
        assertThat(registryCaptor.getValue().getExecutorId()).isEqualTo("demo-app@127.0.0.1:30550");
    }

    @Test
    void register_sameAppDifferentPorts_insertsSeparately() {
        when(executorRegistryMapper.selectOne(any())).thenReturn(null);
        when(executorRegistryMapper.selectList(any())).thenReturn(List.of());

        RegisterDTO dto = new RegisterDTO();
        dto.setExecutorId("demo-app@127.0.0.1:30551");
        dto.setHost("127.0.0.1");
        dto.setPort(30551);
        dto.setAppCode("demo-app");

        registryService.register(dto, 1L);

        verify(executorRegistryMapper).insert(registryCaptor.capture());
        assertThat(registryCaptor.getValue().getExecutorPort()).isEqualTo(30551);
    }

    @Test
    void heartbeat() {
        ExecutorRegistryPO existing = new ExecutorRegistryPO();
        existing.setId(1L);
        existing.setExecutorId("executor-1");
        existing.setStatus(RegistryConstants.STATUS_ONLINE);
        when(executorRegistryMapper.selectOne(any())).thenReturn(existing);

        HeartbeatDTO dto = new HeartbeatDTO();
        dto.setExecutorId("executor-1");

        registryService.heartbeat(dto);

        verify(executorRegistryMapper).updateById(existing);
    }

    @Test
    void heartbeat_unregisteredExecutor() {
        when(executorRegistryMapper.selectOne(any())).thenReturn(null);

        HeartbeatDTO dto = new HeartbeatDTO();
        dto.setExecutorId("unknown");

        assertThatThrownBy(() -> registryService.heartbeat(dto))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EXECUTOR_NOT_FOUND);
    }

    @Test
    void deregister() {
        ExecutorRegistryPO existing = new ExecutorRegistryPO();
        existing.setId(1L);
        existing.setExecutorId("executor-1");
        existing.setStatus(RegistryConstants.STATUS_ONLINE);
        when(executorRegistryMapper.selectOne(any())).thenReturn(existing);

        registryService.deregister("executor-1");

        assertThat(existing.getStatus()).isEqualTo(RegistryConstants.STATUS_OFFLINE);
        verify(executorRegistryMapper).updateById(existing);
    }

    @Test
    void deregister_unknownExecutor_doesNothing() {
        when(executorRegistryMapper.selectOne(any())).thenReturn(null);

        registryService.deregister("unknown");

        verify(executorRegistryMapper, never()).updateById(any(ExecutorRegistryPO.class));
    }

    @Test
    void updateStatus() {
        ExecutorRegistryPO existing = new ExecutorRegistryPO();
        existing.setId(1L);
        existing.setExecutorId("executor-1");
        existing.setStatus(RegistryConstants.STATUS_ONLINE);
        when(executorRegistryMapper.selectOne(any())).thenReturn(existing);

        registryService.updateStatus("executor-1", RegistryConstants.STATUS_ABNORMAL);

        assertThat(existing.getStatus()).isEqualTo(RegistryConstants.STATUS_ABNORMAL);
        verify(executorRegistryMapper).updateById(existing);
    }

    @Test
    void updateStatus_invalidStatus() {
        ExecutorRegistryPO existing = new ExecutorRegistryPO();
        existing.setExecutorId("executor-1");
        when(executorRegistryMapper.selectOne(any())).thenReturn(existing);

        assertThatThrownBy(() -> registryService.updateStatus("executor-1", 999))
                .isInstanceOf(BizException.class);
    }

    @Test
    void updateStatus_executorNotFound() {
        when(executorRegistryMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> registryService.updateStatus("unknown", 0))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EXECUTOR_NOT_FOUND);
    }
}
