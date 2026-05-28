package com.zestflow.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zestflow.admin.constant.ErrorCode;
import com.zestflow.admin.model.entity.ExecutorRegistryPO;
import com.zestflow.admin.model.entity.ModulePO;
import com.zestflow.admin.repository.ExecutorRegistryMapper;
import com.zestflow.admin.repository.ModuleMapper;
import com.zestflow.admin.service.RegistryService;
import com.zestflow.common.constant.RegistryConstants;
import com.zestflow.common.exception.BizException;
import com.zestflow.common.model.dto.HeartbeatDTO;
import com.zestflow.common.model.dto.RegisterDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegistryServiceImpl implements RegistryService {

    private final ExecutorRegistryMapper executorRegistryMapper;
    private final ModuleMapper moduleMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void register(RegisterDTO dto) {
        ExecutorRegistryPO existing = findById(dto.getExecutorId());

        if (existing != null) {
            // 已存在 → 更新心跳和地址
            existing.setExecutorHost(dto.getHost());
            existing.setExecutorPort(dto.getPort());
            existing.setStatus(RegistryConstants.STATUS_ONLINE);
            existing.setLastHeartbeat(LocalDateTime.now());
            if (dto.getAppName() != null) existing.setAppName(dto.getAppName());
            // 查找或自动创建模块并绑定
            linkModuleByCode(existing, dto);
            executorRegistryMapper.updateById(existing);
            log.info("执行器重新注册 executorId={} host={}:{}", dto.getExecutorId(), dto.getHost(), dto.getPort());
        } else {
            // 新执行器 → 插入
            ExecutorRegistryPO po = new ExecutorRegistryPO();
            po.setExecutorId(dto.getExecutorId());
            po.setAppName(dto.getAppName());
            po.setExecutorHost(dto.getHost());
            po.setExecutorPort(dto.getPort());
            po.setStatus(RegistryConstants.STATUS_ONLINE);
            po.setLastHeartbeat(LocalDateTime.now());
            // 查找或自动创建模块并绑定
            linkModuleByCode(po, dto);
            executorRegistryMapper.insert(po);
            log.info("执行器首次注册 executorId={} appName={} host={}:{}", dto.getExecutorId(), dto.getAppName(), dto.getHost(), dto.getPort());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void heartbeat(HeartbeatDTO dto) {
        ExecutorRegistryPO po = findById(dto.getExecutorId());
        if (po == null) {
            log.warn("心跳来自未注册执行器 executorId={}", dto.getExecutorId());
            throw new BizException(ErrorCode.EXECUTOR_NOT_FOUND);
        }
        po.setStatus(RegistryConstants.STATUS_ONLINE);
        po.setLastHeartbeat(LocalDateTime.now());
        executorRegistryMapper.updateById(po);
        log.debug("执行器心跳 executorId={}", dto.getExecutorId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deregister(String executorId) {
        ExecutorRegistryPO po = findById(executorId);
        if (po == null) return;
        po.setStatus(RegistryConstants.STATUS_OFFLINE);
        po.setLastHeartbeat(LocalDateTime.now());
        executorRegistryMapper.updateById(po);
        log.info("执行器主动下线 executorId={}", executorId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(String executorId, Integer status) {
        ExecutorRegistryPO po = findById(executorId);
        if (po == null) {
            throw new BizException(ErrorCode.EXECUTOR_NOT_FOUND);
        }
        if (status == null || (status != 0 && status != 1 && status != 2)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR);
        }
        po.setStatus(status);
        po.setLastHeartbeat(LocalDateTime.now());
        executorRegistryMapper.updateById(po);
        log.info("执行器状态手动变更为 status={} executorId={}", status, executorId);
    }

    private ExecutorRegistryPO findById(String executorId) {
        return executorRegistryMapper.selectOne(
                new LambdaQueryWrapper<ExecutorRegistryPO>()
                        .eq(ExecutorRegistryPO::getExecutorId, executorId)
                        .last("LIMIT 1")
        );
    }

    /**
     * 通过 moduleCode 查找或自动创建模块，然后绑定到执行器
     * 并发时利用 uk_code 唯一约束 + try-catch 保证线程安全
     */
    private void linkModuleByCode(ExecutorRegistryPO po, RegisterDTO dto) {
        String moduleCode = dto.getModuleCode();
        if (moduleCode == null || moduleCode.isEmpty()) {
            return;
        }
        ModulePO module = moduleMapper.selectOne(
                new LambdaQueryWrapper<ModulePO>()
                        .eq(ModulePO::getCode, moduleCode)
                        .last("LIMIT 1")
        );
        if (module == null) {
            try {
                String moduleName = dto.getModuleName() != null ? dto.getModuleName() : moduleCode;
                module = new ModulePO();
                module.setCode(moduleCode);
                module.setName(moduleName);
                module.setStatus(1);
                module.setCreatedAt(LocalDateTime.now());
                module.setUpdatedAt(LocalDateTime.now());
                moduleMapper.insert(module);
                log.info("执行器注册时自动创建模块 moduleId={} code={} name={}", module.getId(), moduleCode, moduleName);
            } catch (DuplicateKeyException e) {
                // 并发注册时另一个线程已创建，重新查询
                module = moduleMapper.selectOne(
                        new LambdaQueryWrapper<ModulePO>()
                                .eq(ModulePO::getCode, moduleCode)
                                .last("LIMIT 1")
                );
            }
        }
        po.setModuleId(module.getId());
    }
}
