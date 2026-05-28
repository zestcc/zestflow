package com.zestflow.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zestflow.admin.constant.ErrorCode;
import com.zestflow.admin.model.dto.ModuleCreateDTO;
import com.zestflow.admin.model.dto.ModuleUpdateDTO;
import com.zestflow.admin.model.entity.ExecutorRegistryPO;
import com.zestflow.admin.model.entity.ModulePO;
import com.zestflow.admin.model.vo.ModuleVO;
import com.zestflow.admin.repository.ExecutorRegistryMapper;
import com.zestflow.admin.repository.ModuleMapper;
import com.zestflow.admin.service.ModuleService;
import com.zestflow.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModuleServiceImpl implements ModuleService {

    private final ModuleMapper moduleMapper;
    private final ExecutorRegistryMapper executorRegistryMapper;

    @Override
    public List<ModuleVO> listAll() {
        List<ModulePO> list = moduleMapper.selectList(
                new LambdaQueryWrapper<ModulePO>()
                        .orderByAsc(ModulePO::getSortOrder)
        );

        // 批量统计各模块下执行器数量
        List<Long> moduleIds = list.stream().map(ModulePO::getId).collect(Collectors.toList());
        List<ExecutorRegistryPO> allExecutors = moduleIds.isEmpty()
                ? List.of()
                : executorRegistryMapper.selectList(
                        new LambdaQueryWrapper<ExecutorRegistryPO>().in(ExecutorRegistryPO::getModuleId, moduleIds)
                );
        Map<Long, List<ExecutorRegistryPO>> executorMap = allExecutors.stream()
                .collect(Collectors.groupingBy(ExecutorRegistryPO::getModuleId));

        return list.stream()
                .map(po -> toVO(po, executorMap.getOrDefault(po.getId(), List.of())))
                .collect(Collectors.toList());
    }

    @Override
    public ModuleVO getById(Long id) {
        ModulePO po = moduleMapper.selectById(id);
        if (po == null) {
            throw new BizException(ErrorCode.MODULE_NOT_FOUND);
        }
        List<ExecutorRegistryPO> executors = executorRegistryMapper.selectList(
                new LambdaQueryWrapper<ExecutorRegistryPO>().eq(ExecutorRegistryPO::getModuleId, id)
        );
        return toVO(po, executors);
    }

    @Override
    public ModuleVO create(ModuleCreateDTO dto) {
        Long count = moduleMapper.selectCount(
                new LambdaQueryWrapper<ModulePO>().eq(ModulePO::getCode, dto.getCode())
        );
        if (count > 0) {
            throw new BizException(ErrorCode.MODULE_CODE_EXISTS);
        }

        ModulePO po = new ModulePO();
        po.setCode(dto.getCode());
        po.setName(dto.getName());
        po.setDescription(dto.getDescription());
        po.setStatus(dto.getStatus() != null ? dto.getStatus() : 1);
        po.setOwner(dto.getOwner());
        if (dto.getSortOrder() != null) {
            po.setSortOrder(dto.getSortOrder());
        } else {
            ModulePO maxSort = moduleMapper.selectOne(
                    new LambdaQueryWrapper<ModulePO>()
                            .orderByDesc(ModulePO::getSortOrder)
                            .last("LIMIT 1")
            );
            po.setSortOrder(maxSort != null ? maxSort.getSortOrder() + 1 : 1);
        }
        po.setRetryCount(dto.getRetryCount() != null ? dto.getRetryCount() : 5);
        po.setRetryInterval(dto.getRetryInterval() != null ? dto.getRetryInterval() : 60);
        po.setCreatedAt(LocalDateTime.now());
        po.setUpdatedAt(LocalDateTime.now());
        moduleMapper.insert(po);

        log.info("模块创建成功 moduleId={} code={} name={}", po.getId(), dto.getCode(), dto.getName());
        return toVO(po);
    }

    @Override
    public ModuleVO update(Long id, ModuleUpdateDTO dto) {
        ModulePO po = moduleMapper.selectById(id);
        if (po == null) {
            throw new BizException(ErrorCode.MODULE_NOT_FOUND);
        }

        if (dto.getName() != null) po.setName(dto.getName());
        if (dto.getDescription() != null) po.setDescription(dto.getDescription());
        if (dto.getStatus() != null) po.setStatus(dto.getStatus());
        if (dto.getOwner() != null) po.setOwner(dto.getOwner());
        if (dto.getSortOrder() != null) po.setSortOrder(dto.getSortOrder());
        if (dto.getRetryCount() != null) po.setRetryCount(dto.getRetryCount());
        if (dto.getRetryInterval() != null) po.setRetryInterval(dto.getRetryInterval());
        po.setUpdatedAt(LocalDateTime.now());
        moduleMapper.updateById(po);

        // 停用模块时，同时下线该模块下所有执行器
        if (dto.getStatus() != null && dto.getStatus() == 0) {
            List<ExecutorRegistryPO> executors = executorRegistryMapper.selectList(
                    new LambdaQueryWrapper<ExecutorRegistryPO>().eq(ExecutorRegistryPO::getModuleId, id)
            );
            if (!executors.isEmpty()) {
                ExecutorRegistryPO update = new ExecutorRegistryPO();
                update.setStatus(0);
                executorRegistryMapper.update(update,
                        new LambdaQueryWrapper<ExecutorRegistryPO>().eq(ExecutorRegistryPO::getModuleId, id)
                );
                log.info("模块停用触发执行器下线 moduleId={} count={}", id, executors.size());
            }
        }

        log.info("模块更新成功 moduleId={}", id);
        return toVO(po);
    }

    @Override
    public void delete(Long id) {
        ModulePO po = moduleMapper.selectById(id);
        if (po == null) {
            throw new BizException(ErrorCode.MODULE_NOT_FOUND);
        }
        moduleMapper.deleteById(id);
        log.info("模块删除成功 moduleId={} code={}", id, po.getCode());
    }

    private ModuleVO toVO(ModulePO po) {
        return toVO(po, List.of());
    }

    private ModuleVO toVO(ModulePO po, List<ExecutorRegistryPO> executors) {
        int total = executors.size();
        int healthy = (int) executors.stream().filter(e -> e.getStatus() == 1).count();
        int error = (int) executors.stream().filter(e -> e.getStatus() == 2).count();
        int offline = (int) executors.stream().filter(e -> e.getStatus() == 0).count();

        return ModuleVO.builder()
                .id(po.getId())
                .code(po.getCode())
                .name(po.getName())
                .description(po.getDescription())
                .status(po.getStatus())
                .owner(po.getOwner())
                .sortOrder(po.getSortOrder())
                .retryCount(po.getRetryCount())
                .retryInterval(po.getRetryInterval())
                .executorTotal(total)
                .executorHealthy(healthy)
                .executorError(error)
                .executorOffline(offline)
                .createdAt(po.getCreatedAt())
                .updatedAt(po.getUpdatedAt())
                .build();
    }
}
