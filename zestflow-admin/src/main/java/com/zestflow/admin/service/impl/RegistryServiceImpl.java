package com.zestflow.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zestflow.admin.constant.ErrorCode;
import com.zestflow.admin.model.entity.ExecutorRegistryPO;
import com.zestflow.admin.repository.ExecutorRegistryMapper;
import com.zestflow.admin.service.DictTypeService;
import com.zestflow.admin.service.RegistryService;
import com.zestflow.common.constant.RegistryConstants;
import com.zestflow.common.exception.BizException;
import com.zestflow.common.model.dto.ComponentDTO;
import com.zestflow.common.model.dto.HeartbeatDTO;
import com.zestflow.common.model.dto.RegisterDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegistryServiceImpl implements RegistryService {

    private final ExecutorRegistryMapper executorRegistryMapper;
    private final DictTypeService dictTypeService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void register(RegisterDTO dto, Long tenantId) {
        ExecutorRegistryPO existing = findById(dto.getExecutorId());
        if (existing != null) {
            updateExisting(existing, dto);
            return;
        }

        List<ExecutorRegistryPO> byAddress = findByAddress(dto.getHost(), dto.getPort(), dto.getAppCode());
        if (!byAddress.isEmpty()) {
            ExecutorRegistryPO primary = byAddress.get(0);
            for (int i = 1; i < byAddress.size(); i++) {
                executorRegistryMapper.deleteById(byAddress.get(i).getId());
            }
            primary.setExecutorId(dto.getExecutorId());
            updateExisting(primary, dto);
            log.info("执行器重新注册（按地址合并）executorId={} host={}:{}",
                    dto.getExecutorId(), dto.getHost(), dto.getPort());
            return;
        }

        ExecutorRegistryPO po = new ExecutorRegistryPO();
        po.setExecutorId(dto.getExecutorId());
        po.setAppCode(dto.getAppCode());
        po.setAppName(dto.getAppName());
        po.setExecutorHost(dto.getHost());
        po.setExecutorPort(dto.getPort());
        po.setStatus(RegistryConstants.STATUS_ONLINE);
        po.setLastHeartbeat(LocalDateTime.now());
        po.setTenantId(tenantId != null ? tenantId : 1L);
        executorRegistryMapper.insert(po);
        log.info("执行器首次注册 executorId={} appCode={} appName={} host={}:{}",
                dto.getExecutorId(), dto.getAppCode(), dto.getAppName(), dto.getHost(), dto.getPort());

        if (dto.getAppCode() != null) {
            String appLabel = dto.getAppName() != null && !dto.getAppName().isEmpty()
                    ? dto.getAppName() : dto.getAppCode();
            dictTypeService.ensureDictData("app_type", dto.getAppCode(), appLabel);
        }
        syncComponentDict(dto);
    }

    private void updateExisting(ExecutorRegistryPO po, RegisterDTO dto) {
        po.setExecutorHost(dto.getHost());
        po.setExecutorPort(dto.getPort());
        po.setStatus(RegistryConstants.STATUS_ONLINE);
        po.setLastHeartbeat(LocalDateTime.now());
        if (dto.getAppName() != null) po.setAppName(dto.getAppName());
        if (dto.getAppCode() != null) po.setAppCode(dto.getAppCode());
        syncComponentDict(dto);
        executorRegistryMapper.updateById(po);
        log.info("执行器重新注册 executorId={} host={}:{}", dto.getExecutorId(), dto.getHost(), dto.getPort());
    }

    private List<ExecutorRegistryPO> findByAddress(String host, int port, String appCode) {
        LambdaQueryWrapper<ExecutorRegistryPO> wrapper = new LambdaQueryWrapper<ExecutorRegistryPO>()
                .eq(ExecutorRegistryPO::getExecutorHost, host)
                .eq(ExecutorRegistryPO::getExecutorPort, port);
        if (appCode != null && !appCode.isEmpty()) {
            wrapper.eq(ExecutorRegistryPO::getAppCode, appCode);
        }
        return executorRegistryMapper.selectList(wrapper);
    }

    private void syncComponentDict(RegisterDTO dto) {
        if (dto.getComponents() != null) {
            for (ComponentDTO comp : dto.getComponents()) {
                dictTypeService.ensureDictData("component_type", comp.getComponentType(), comp.getComponentType());
            }
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
}
