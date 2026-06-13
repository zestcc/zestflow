package com.zestflow.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zestflow.admin.constant.ErrorCode;
import com.zestflow.admin.model.entity.ExecutorRegistryPO;
import com.zestflow.admin.registry.DeclaredChainKeysSupport;
import com.zestflow.admin.registry.RegistryLifecycleService;
import com.zestflow.admin.registry.RegistryLiveStore;
import com.zestflow.admin.registry.RegistryOnlineQuerySupport;
import com.zestflow.admin.repository.ExecutorRegistryMapper;
import com.zestflow.admin.service.DictTypeService;
import com.zestflow.admin.service.RegistryService;
import com.zestflow.common.constant.RegistryConstants;
import com.zestflow.common.exception.BizException;
import com.zestflow.common.model.dto.ComponentDTO;
import com.zestflow.common.model.dto.HeartbeatDTO;
import com.zestflow.common.model.dto.PeerExecutorDTO;
import com.zestflow.common.model.dto.RegisterDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegistryServiceImpl implements RegistryService {

    private final ExecutorRegistryMapper executorRegistryMapper;
    private final DictTypeService dictTypeService;
    private final RegistryLiveStore liveStore;
    private final RegistryLifecycleService registryLifecycleService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void register(RegisterDTO dto, Long tenantId) {
        liveStore.touchExecutor(dto.getExecutorId());

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
            if (updateExisting(primary, dto)) {
                log.info("执行器重新注册（按地址合并）executorId={} host={}:{}",
                        dto.getExecutorId(), dto.getHost(), dto.getPort());
            }
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
        po.setDeclaredChainKeys(DeclaredChainKeysSupport.toJson(
                DeclaredChainKeysSupport.normalize(dto.getDeclaredChainKeys())));
        executorRegistryMapper.insert(po);
        registryLifecycleService.onExecutorHeartbeat(dto.getExecutorId());
        log.info("执行器首次注册 executorId={} appCode={} appName={} host={}:{}",
                dto.getExecutorId(), dto.getAppCode(), dto.getAppName(), dto.getHost(), dto.getPort());

        if (dto.getAppCode() != null) {
            String appLabel = dto.getAppName() != null && !dto.getAppName().isEmpty()
                    ? dto.getAppName() : dto.getAppCode();
            dictTypeService.ensureDictData("app_type", dto.getAppCode(), appLabel);
        }
        syncComponentDict(dto);
    }

    /** @return {@code true} 表示写入了 DB */
    private boolean updateExisting(ExecutorRegistryPO po, RegisterDTO dto) {
        boolean metadataChanged = !Objects.equals(po.getExecutorId(), dto.getExecutorId())
                || !Objects.equals(po.getExecutorHost(), dto.getHost())
                || !Objects.equals(po.getExecutorPort(), dto.getPort())
                || (hasText(dto.getAppName()) && !Objects.equals(po.getAppName(), dto.getAppName()))
                || (hasText(dto.getAppCode()) && !Objects.equals(po.getAppCode(), dto.getAppCode()));
        String newDeclaredJson = DeclaredChainKeysSupport.toJson(
                DeclaredChainKeysSupport.normalize(dto.getDeclaredChainKeys()));
        boolean declaredChanged = !Objects.equals(po.getDeclaredChainKeys(), newDeclaredJson);
        boolean needRevive = !Objects.equals(po.getStatus(), RegistryConstants.STATUS_ONLINE);

        if (!metadataChanged && !needRevive && !declaredChanged) {
            log.debug("执行器 register 幂等刷新 executorId={}", dto.getExecutorId());
            return false;
        }

        po.setExecutorId(dto.getExecutorId());
        po.setExecutorHost(dto.getHost());
        po.setExecutorPort(dto.getPort());
        po.setStatus(RegistryConstants.STATUS_ONLINE);
        po.setLastHeartbeat(LocalDateTime.now());
        if (dto.getAppName() != null) {
            po.setAppName(dto.getAppName());
        }
        if (dto.getAppCode() != null) {
            po.setAppCode(dto.getAppCode());
        }
        po.setDeclaredChainKeys(newDeclaredJson);
        if (metadataChanged) {
            syncComponentDict(dto);
        }
        executorRegistryMapper.updateById(po);
        registryLifecycleService.onExecutorHeartbeat(dto.getExecutorId());
        if (needRevive) {
            log.info("执行器恢复在线 executorId={} host={}:{}", dto.getExecutorId(), dto.getHost(), dto.getPort());
        } else {
            log.info("执行器元数据更新 executorId={} host={}:{}", dto.getExecutorId(), dto.getHost(), dto.getPort());
        }
        return true;
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
    public void heartbeat(HeartbeatDTO dto) {
        String executorId = dto.getExecutorId();
        if (!liveStore.tracksExecutor(executorId)) {
            ExecutorRegistryPO po = findById(executorId);
            if (po == null) {
                log.warn("心跳来自未注册执行器 executorId={}", executorId);
                throw new BizException(ErrorCode.EXECUTOR_NOT_FOUND);
            }
            if (po.getStatus() == RegistryConstants.STATUS_OFFLINE) {
                log.warn("心跳来自已下线执行器 executorId={}", executorId);
                throw new BizException(ErrorCode.EXECUTOR_NOT_FOUND);
            }
        }
        liveStore.touchExecutor(executorId);
        if (dto.getDeclaredChainKeys() != null) {
            persistDeclaredChainKeys(executorId, dto.getDeclaredChainKeys());
        }
        registryLifecycleService.onExecutorHeartbeat(executorId);
        log.trace("执行器心跳 executorId={}", executorId);
    }

    private void persistDeclaredChainKeys(String executorId, List<String> declaredChainKeys) {
        ExecutorRegistryPO po = findById(executorId);
        if (po == null) {
            return;
        }
        String json = DeclaredChainKeysSupport.toJson(DeclaredChainKeysSupport.normalize(declaredChainKeys));
        if (Objects.equals(po.getDeclaredChainKeys(), json)) {
            return;
        }
        po.setDeclaredChainKeys(json);
        executorRegistryMapper.updateById(po);
        log.debug("执行器声明链更新 executorId={} keys={}", executorId, declaredChainKeys.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deregister(String executorId) {
        liveStore.removeExecutor(executorId);
        registryLifecycleService.onExecutorRemoved(executorId);
        ExecutorRegistryPO po = findById(executorId);
        if (po == null) {
            return;
        }
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
        if (status == RegistryConstants.STATUS_ONLINE) {
            liveStore.touchExecutor(executorId);
        } else {
            liveStore.removeExecutor(executorId);
        }
        po.setStatus(status);
        po.setLastHeartbeat(LocalDateTime.now());
        executorRegistryMapper.updateById(po);
        log.info("执行器状态手动变更为 status={} executorId={}", status, executorId);
    }

    @Override
    public List<PeerExecutorDTO> listOnlinePeers(String appCode) {
        return RegistryOnlineQuerySupport.listLiveOnlineExecutors(executorRegistryMapper, liveStore, appCode)
                .stream()
                .map(po -> PeerExecutorDTO.builder()
                        .executorId(po.getExecutorId())
                        .appCode(po.getAppCode())
                        .host(po.getExecutorHost())
                        .port(po.getExecutorPort() != null ? po.getExecutorPort() : 0)
                        .build())
                .sorted(Comparator.comparing(PeerExecutorDTO::getExecutorId, Comparator.nullsLast(String::compareTo)))
                .toList();
    }

    private ExecutorRegistryPO findById(String executorId) {
        return executorRegistryMapper.selectOne(
                new LambdaQueryWrapper<ExecutorRegistryPO>()
                        .eq(ExecutorRegistryPO::getExecutorId, executorId)
                        .last("LIMIT 1")
        );
    }

    private static boolean hasText(String value) {
        return value != null && !value.isEmpty();
    }
}
