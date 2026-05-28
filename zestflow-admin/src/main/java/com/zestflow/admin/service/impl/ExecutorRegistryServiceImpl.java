package com.zestflow.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zestflow.admin.model.entity.ExecutorRegistryPO;
import com.zestflow.admin.model.entity.ModulePO;
import com.zestflow.admin.model.vo.ExecutorRegistryVO;
import com.zestflow.admin.repository.ExecutorRegistryMapper;
import com.zestflow.admin.repository.ModuleMapper;
import com.zestflow.admin.service.ExecutorRegistryService;
import com.zestflow.common.exception.BizException;
import com.zestflow.admin.constant.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExecutorRegistryServiceImpl implements ExecutorRegistryService {

    private final ExecutorRegistryMapper executorRegistryMapper;
    private final ModuleMapper moduleMapper;

    @Override
    public List<ExecutorRegistryVO> listByModuleId(Long moduleId) {
        ModulePO module = moduleMapper.selectById(moduleId);
        if (module == null) {
            return Collections.emptyList();
        }

        List<ExecutorRegistryPO> list = executorRegistryMapper.selectList(
                new LambdaQueryWrapper<ExecutorRegistryPO>()
                        .eq(ExecutorRegistryPO::getModuleId, moduleId)
                        .orderByDesc(ExecutorRegistryPO::getLastHeartbeat)
        );

        log.info("查询模块执行器列表 moduleId={} count={}", moduleId, list.size());
        return list.stream().map(po -> ExecutorRegistryVO.builder()
                .id(po.getId())
                .moduleId(po.getModuleId())
                .moduleCode(module.getCode())
                .moduleName(module.getName())
                .executorId(po.getExecutorId())
                .executorHost(po.getExecutorHost())
                .executorPort(po.getExecutorPort())
                .status(po.getStatus())
                .retryCount(po.getRetryCount())
                .lastHeartbeat(po.getLastHeartbeat())
                .createdAt(po.getCreatedAt())
                .build()
        ).collect(Collectors.toList());
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        ExecutorRegistryPO po = executorRegistryMapper.selectById(id);
        if (po == null) {
            throw new BizException(ErrorCode.EXECUTOR_NOT_FOUND);
        }
        po.setStatus(status);
        po.setUpdatedAt(LocalDateTime.now());
        executorRegistryMapper.updateById(po);
        log.info("执行器状态变更 executorId={} status={}", po.getExecutorId(), status);
    }
}
