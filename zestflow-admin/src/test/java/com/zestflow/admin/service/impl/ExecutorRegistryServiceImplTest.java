package com.zestflow.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zestflow.admin.constant.ErrorCode;
import com.zestflow.admin.model.entity.ExecutorRegistryPO;
import com.zestflow.admin.model.entity.ModulePO;
import com.zestflow.admin.model.vo.ExecutorRegistryVO;
import com.zestflow.admin.repository.ExecutorRegistryMapper;
import com.zestflow.admin.repository.ModuleMapper;
import com.zestflow.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExecutorRegistryServiceImplTest {

    @Mock private ExecutorRegistryMapper executorRegistryMapper;
    @Mock private ModuleMapper moduleMapper;

    private ExecutorRegistryServiceImpl executorRegistryService;

    @BeforeEach
    void setUp() {
        executorRegistryService = new ExecutorRegistryServiceImpl(executorRegistryMapper, moduleMapper);
    }

    @Test
    void listByModuleId() {
        ModulePO module = new ModulePO();
        module.setId(1L);
        module.setCode("test-module");
        module.setName("测试模块");
        when(moduleMapper.selectById(1L)).thenReturn(module);

        ExecutorRegistryPO exec = new ExecutorRegistryPO();
        exec.setId(10L);
        exec.setExecutorId("executor-1");
        exec.setModuleId(1L);
        exec.setAppName("test-app");
        exec.setStatus(1);
        when(executorRegistryMapper.selectList(any())).thenReturn(List.of(exec));

        List<ExecutorRegistryVO> list = executorRegistryService.listByModuleId(1L);

        assertThat(list).hasSize(1);
        assertThat(list.get(0).getExecutorId()).isEqualTo("executor-1");
        assertThat(list.get(0).getModuleCode()).isEqualTo("test-module");
        assertThat(list.get(0).getModuleName()).isEqualTo("测试模块");
    }

    @Test
    void listByModuleId_moduleNotFound() {
        when(moduleMapper.selectById(999L)).thenReturn(null);

        List<ExecutorRegistryVO> list = executorRegistryService.listByModuleId(999L);

        assertThat(list).isEmpty();
    }

    @Test
    void listByModuleId_emptyList() {
        ModulePO module = new ModulePO();
        module.setId(1L);
        module.setCode("test");
        when(moduleMapper.selectById(1L)).thenReturn(module);
        when(executorRegistryMapper.selectList(any())).thenReturn(List.of());

        List<ExecutorRegistryVO> list = executorRegistryService.listByModuleId(1L);

        assertThat(list).isEmpty();
    }

    @Test
    void updateStatus() {
        ExecutorRegistryPO exec = new ExecutorRegistryPO();
        exec.setId(10L);
        exec.setExecutorId("executor-1");
        exec.setStatus(1);
        when(executorRegistryMapper.selectById(10L)).thenReturn(exec);

        executorRegistryService.updateStatus(10L, 0);

        verify(executorRegistryMapper).updateById(exec);
        assertThat(exec.getStatus()).isEqualTo(0);
    }

    @Test
    void updateStatus_notFound() {
        when(executorRegistryMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> executorRegistryService.updateStatus(999L, 0))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EXECUTOR_NOT_FOUND);
    }
}
