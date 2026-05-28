package com.zestflow.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zestflow.admin.model.entity.ExecutorRegistryPO;
import com.zestflow.admin.model.vo.DashboardStatsVO;
import com.zestflow.admin.repository.ExecutorRegistryMapper;
import com.zestflow.admin.repository.ModuleMapper;
import com.zestflow.admin.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final ModuleMapper moduleMapper;
    private final ExecutorRegistryMapper executorRegistryMapper;

    @Override
    public DashboardStatsVO getStats() {
        long totalModules = moduleMapper.selectCount(null);

        long totalExecutors = executorRegistryMapper.selectCount(null);
        long healthyExecutors = executorRegistryMapper.selectCount(
                new LambdaQueryWrapper<ExecutorRegistryPO>().eq(ExecutorRegistryPO::getStatus, 1)
        );
        long errorExecutors = executorRegistryMapper.selectCount(
                new LambdaQueryWrapper<ExecutorRegistryPO>().eq(ExecutorRegistryPO::getStatus, 2)
        );
        long offlineExecutors = executorRegistryMapper.selectCount(
                new LambdaQueryWrapper<ExecutorRegistryPO>().eq(ExecutorRegistryPO::getStatus, 0)
        );

        return DashboardStatsVO.builder()
                .totalModules(totalModules)
                .totalExecutors(totalExecutors)
                .healthyExecutors(healthyExecutors)
                .errorExecutors(errorExecutors)
                .offlineExecutors(offlineExecutors)
                .build();
    }
}
