package com.zestflow.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zestflow.admin.constant.ErrorCode;
import com.zestflow.admin.model.entity.ExecutorRegistryPO;
import com.zestflow.admin.model.vo.ExecutorRegistryVO;
import com.zestflow.admin.repository.ExecutorRegistryMapper;
import com.zestflow.admin.service.ExecutorRegistryService;
import com.zestflow.admin.service.TenantAppContext;
import com.zestflow.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExecutorRegistryServiceImpl implements ExecutorRegistryService {

    private final ExecutorRegistryMapper executorRegistryMapper;
    private final TenantAppContext tenantAppContext;

    @Override
    public List<ExecutorRegistryVO> listAll() {
        Set<String> accessibleCodes = tenantAppContext.getCurrentUserAppCodes();
        LambdaQueryWrapper<ExecutorRegistryPO> wrapper = new LambdaQueryWrapper<>();
        if (accessibleCodes != null && !accessibleCodes.isEmpty()) {
            wrapper.in(ExecutorRegistryPO::getAppCode, accessibleCodes);
        }
        wrapper.orderByDesc(ExecutorRegistryPO::getLastHeartbeat);
        List<ExecutorRegistryPO> list = executorRegistryMapper.selectList(wrapper);
        return list.stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public ExecutorRegistryVO getByExecutorId(String executorId) {
        ExecutorRegistryPO po = executorRegistryMapper.selectOne(
                new LambdaQueryWrapper<ExecutorRegistryPO>()
                        .eq(ExecutorRegistryPO::getExecutorId, executorId)
                        .last("LIMIT 1")
        );
        if (po == null) throw new BizException(ErrorCode.EXECUTOR_NOT_FOUND);
        return toVO(po);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(String executorId, Integer status) {
        ExecutorRegistryPO po = executorRegistryMapper.selectOne(
                new LambdaQueryWrapper<ExecutorRegistryPO>()
                        .eq(ExecutorRegistryPO::getExecutorId, executorId)
                        .last("LIMIT 1")
        );
        if (po == null) throw new BizException(ErrorCode.EXECUTOR_NOT_FOUND);
        if (status == null || (status != 0 && status != 1 && status != 2)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR);
        }
        po.setStatus(status);
        po.setLastHeartbeat(java.time.LocalDateTime.now());
        executorRegistryMapper.updateById(po);
    }

    @Override
    public List<Map<String, String>> listDistinctApps() {
        List<ExecutorRegistryPO> list = executorRegistryMapper.selectList(
                new QueryWrapper<ExecutorRegistryPO>()
                        .select("DISTINCT app_code, app_name")
                        .isNotNull("app_code")
                        .orderByAsc("app_code")
        );
        return list.stream().map(po -> {
            Map<String, String> map = new java.util.HashMap<>();
            map.put("appCode", po.getAppCode());
            map.put("appName", po.getAppName() != null ? po.getAppName() : po.getAppCode());
            return map;
        }).collect(Collectors.toList());
    }

    private ExecutorRegistryVO toVO(ExecutorRegistryPO po) {
        return ExecutorRegistryVO.builder()
                .id(po.getId())
                .executorId(po.getExecutorId())
                .appCode(po.getAppCode())
                .appName(po.getAppName() != null ? po.getAppName() : po.getAppCode())
                .executorHost(po.getExecutorHost())
                .executorPort(po.getExecutorPort())
                .status(po.getStatus())
                .lastHeartbeat(po.getLastHeartbeat())
                .updatedBy(po.getUpdatedBy())
                .createdAt(po.getCreatedAt())
                .build();
    }
}
