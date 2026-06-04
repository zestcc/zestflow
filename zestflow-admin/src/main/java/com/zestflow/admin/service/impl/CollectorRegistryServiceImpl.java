package com.zestflow.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zestflow.admin.constant.ErrorCode;
import com.zestflow.admin.model.entity.CollectorRegistryPO;
import com.zestflow.admin.model.vo.CollectorRegistryVO;
import com.zestflow.admin.registry.RegistryLiveStore;
import com.zestflow.admin.registry.RegistryLiveTimeSupport;
import com.zestflow.admin.repository.CollectorRegistryMapper;
import com.zestflow.admin.service.CollectorRegistryService;
import com.zestflow.admin.service.TenantAppContext;
import com.zestflow.common.constant.RegistryConstants;
import com.zestflow.common.exception.BizException;
import com.zestflow.common.model.dto.HeartbeatDTO;
import com.zestflow.common.model.dto.RegisterDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CollectorRegistryServiceImpl implements CollectorRegistryService {

    private final CollectorRegistryMapper collectorRegistryMapper;
    private final TenantAppContext tenantAppContext;
    private final RegistryLiveStore liveStore;

    @Transactional(rollbackFor = Exception.class)
    public void register(RegisterDTO dto) {
        liveStore.touchCollector(dto.getExecutorId());

        CollectorRegistryPO existing = findById(dto.getExecutorId());
        if (existing != null) {
            updateExisting(existing, dto);
            return;
        }

        List<CollectorRegistryPO> byAddress = collectorRegistryMapper.selectList(
                new LambdaQueryWrapper<CollectorRegistryPO>()
                        .eq(CollectorRegistryPO::getCollectorHost, dto.getHost())
                        .eq(CollectorRegistryPO::getCollectorPort, dto.getPort())
        );
        if (!byAddress.isEmpty()) {
            CollectorRegistryPO primary = byAddress.get(0);
            for (int i = 1; i < byAddress.size(); i++) {
                collectorRegistryMapper.deleteById(byAddress.get(i).getId());
            }
            primary.setCollectorId(dto.getExecutorId());
            if (updateExisting(primary, dto)) {
                log.info("采集器重新注册（兼容旧格式）collectorId={} host={}:{}",
                        dto.getExecutorId(), dto.getHost(), dto.getPort());
            }
            return;
        }

        CollectorRegistryPO po = new CollectorRegistryPO();
        po.setCollectorId(dto.getExecutorId());
        po.setAppCode(dto.getAppCode());
        po.setAppName(dto.getAppName());
        po.setCollectorHost(dto.getHost());
        po.setCollectorPort(dto.getPort());
        po.setStatus(RegistryConstants.STATUS_ONLINE);
        po.setLastHeartbeat(LocalDateTime.now());
        collectorRegistryMapper.insert(po);
        log.info("采集器首次注册 collectorId={} appCode={} appName={} host={}:{}",
                dto.getExecutorId(), dto.getAppCode(), dto.getAppName(), dto.getHost(), dto.getPort());
    }

    /** @return {@code true} 表示写入了 DB */
    private boolean updateExisting(CollectorRegistryPO po, RegisterDTO dto) {
        boolean metadataChanged = !Objects.equals(po.getCollectorId(), dto.getExecutorId())
                || !Objects.equals(po.getCollectorHost(), dto.getHost())
                || !Objects.equals(po.getCollectorPort(), dto.getPort())
                || (hasText(dto.getAppName()) && !Objects.equals(po.getAppName(), dto.getAppName()))
                || (hasText(dto.getAppCode()) && !Objects.equals(po.getAppCode(), dto.getAppCode()));
        boolean needRevive = !Objects.equals(po.getStatus(), RegistryConstants.STATUS_ONLINE);

        if (!metadataChanged && !needRevive) {
            log.debug("采集器 register 幂等刷新 collectorId={}", dto.getExecutorId());
            return false;
        }

        po.setCollectorId(dto.getExecutorId());
        po.setCollectorHost(dto.getHost());
        po.setCollectorPort(dto.getPort());
        po.setStatus(RegistryConstants.STATUS_ONLINE);
        po.setLastHeartbeat(LocalDateTime.now());
        if (dto.getAppName() != null) {
            po.setAppName(dto.getAppName());
        }
        if (dto.getAppCode() != null) {
            po.setAppCode(dto.getAppCode());
        }
        collectorRegistryMapper.updateById(po);
        if (needRevive) {
            log.info("采集器恢复在线 collectorId={} host={}:{}", dto.getExecutorId(), dto.getHost(), dto.getPort());
        } else {
            log.info("采集器元数据更新 collectorId={} host={}:{}", dto.getExecutorId(), dto.getHost(), dto.getPort());
        }
        return true;
    }

    public void heartbeat(HeartbeatDTO dto) {
        String collectorId = dto.getExecutorId();
        if (!liveStore.tracksCollector(collectorId)) {
            CollectorRegistryPO po = findById(collectorId);
            if (po == null) {
                log.warn("心跳来自未注册采集器 collectorId={}", collectorId);
                throw new BizException(ErrorCode.EXECUTOR_NOT_FOUND);
            }
            if (po.getStatus() == RegistryConstants.STATUS_OFFLINE) {
                log.warn("心跳来自已下线采集器 collectorId={}", collectorId);
                throw new BizException(ErrorCode.EXECUTOR_NOT_FOUND);
            }
        }
        liveStore.touchCollector(collectorId);
        log.trace("采集器心跳 collectorId={}", collectorId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deregister(String collectorId) {
        liveStore.removeCollector(collectorId);
        CollectorRegistryPO po = findById(collectorId);
        if (po == null) {
            return;
        }
        po.setStatus(RegistryConstants.STATUS_OFFLINE);
        po.setLastHeartbeat(LocalDateTime.now());
        collectorRegistryMapper.updateById(po);
        log.info("采集器主动下线 collectorId={}", collectorId);
    }

    @Override
    public CollectorRegistryVO getByCollectorId(String collectorId) {
        CollectorRegistryPO po = findById(collectorId);
        if (po == null) {
            throw new BizException(ErrorCode.EXECUTOR_NOT_FOUND);
        }
        return enrichVO(po);
    }

    @Override
    public List<CollectorRegistryVO> listAllOnline() {
        Set<String> aliveIds = liveStore.aliveCollectorIds();
        if (aliveIds.isEmpty()) {
            return List.of();
        }
        List<CollectorRegistryPO> list = collectorRegistryMapper.selectList(
                new LambdaQueryWrapper<CollectorRegistryPO>()
                        .eq(CollectorRegistryPO::getStatus, RegistryConstants.STATUS_ONLINE)
                        .in(CollectorRegistryPO::getCollectorId, aliveIds)
        );
        return list.stream().map(this::enrichVO).collect(Collectors.toList());
    }

    @Override
    public List<CollectorRegistryVO> listOnlineByAppCode(String appCode) {
        Set<String> aliveIds = liveStore.aliveCollectorIds();
        if (aliveIds.isEmpty()) {
            return List.of();
        }
        List<CollectorRegistryPO> list = collectorRegistryMapper.selectList(
                new LambdaQueryWrapper<CollectorRegistryPO>()
                        .eq(CollectorRegistryPO::getStatus, RegistryConstants.STATUS_ONLINE)
                        .eq(CollectorRegistryPO::getAppCode, appCode)
                        .in(CollectorRegistryPO::getCollectorId, aliveIds)
        );
        return list.stream().map(this::enrichVO).collect(Collectors.toList());
    }

    @Override
    public List<CollectorRegistryVO> listAll() {
        Set<String> accessibleCodes = tenantAppContext.getCurrentUserAppCodes();
        LambdaQueryWrapper<CollectorRegistryPO> wrapper = new LambdaQueryWrapper<>();
        if (accessibleCodes != null && !accessibleCodes.isEmpty()) {
            wrapper.in(CollectorRegistryPO::getAppCode, accessibleCodes);
        }
        wrapper.orderByDesc(CollectorRegistryPO::getLastHeartbeat);
        List<CollectorRegistryPO> list = collectorRegistryMapper.selectList(wrapper);
        return list.stream().map(this::enrichVO).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        CollectorRegistryPO po = collectorRegistryMapper.selectById(id);
        if (po == null) {
            throw new BizException(ErrorCode.EXECUTOR_NOT_FOUND);
        }
        if (status == null || (status != 0 && status != 1 && status != 2)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR);
        }
        if (status == RegistryConstants.STATUS_ONLINE) {
            liveStore.touchCollector(po.getCollectorId());
        } else {
            liveStore.removeCollector(po.getCollectorId());
        }
        po.setStatus(status);
        po.setLastHeartbeat(LocalDateTime.now());
        collectorRegistryMapper.updateById(po);
    }

    private CollectorRegistryPO findById(String collectorId) {
        return collectorRegistryMapper.selectOne(
                new LambdaQueryWrapper<CollectorRegistryPO>()
                        .eq(CollectorRegistryPO::getCollectorId, collectorId)
                        .last("LIMIT 1")
        );
    }

    private CollectorRegistryVO toVO(CollectorRegistryPO po, String appCode, String appName) {
        return CollectorRegistryVO.builder()
                .id(po.getId())
                .collectorId(po.getCollectorId())
                .appCode(appCode)
                .appName(appName)
                .collectorHost(po.getCollectorHost())
                .collectorPort(po.getCollectorPort())
                .status(resolveDisplayStatus(po))
                .lastHeartbeat(RegistryLiveTimeSupport.resolveLastHeartbeat(
                        liveStore, po.getCollectorId(), po.getLastHeartbeat(), false))
                .updatedBy(po.getUpdatedBy())
                .createdAt(po.getCreatedAt())
                .updatedAt(po.getUpdatedAt())
                .build();
    }

    private int resolveDisplayStatus(CollectorRegistryPO po) {
        if (po.getStatus() == RegistryConstants.STATUS_ONLINE
                && !liveStore.isCollectorAlive(po.getCollectorId())) {
            return RegistryConstants.STATUS_ABNORMAL;
        }
        return po.getStatus();
    }

    private CollectorRegistryVO enrichVO(CollectorRegistryPO po) {
        return toVO(po, po.getAppCode(), po.getAppName());
    }

    private static boolean hasText(String value) {
        return value != null && !value.isEmpty();
    }
}
