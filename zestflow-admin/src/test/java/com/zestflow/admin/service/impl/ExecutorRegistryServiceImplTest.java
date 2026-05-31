package com.zestflow.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zestflow.admin.constant.ErrorCode;
import com.zestflow.admin.model.entity.ExecutorRegistryPO;
import com.zestflow.admin.repository.ExecutorRegistryMapper;
import com.zestflow.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExecutorRegistryServiceImplTest {

    @Mock private ExecutorRegistryMapper executorRegistryMapper;

    private ExecutorRegistryServiceImpl executorRegistryService;

    @BeforeEach
    void setUp() {
        executorRegistryService = new ExecutorRegistryServiceImpl(executorRegistryMapper);
    }

    @Test
    void updateStatus() {
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
    void updateStatus_notFound() {
        when(executorRegistryMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> executorRegistryService.updateStatus("unknown", 0))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EXECUTOR_NOT_FOUND);
    }
}
