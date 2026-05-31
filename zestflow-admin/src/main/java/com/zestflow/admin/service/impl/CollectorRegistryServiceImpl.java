package com.zestflow.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zestflow.admin.constant.ErrorCode;
import com.zestflow.admin.model.entity.CollectorRegistryPO;
import com.zestflow.admin.model.vo.CollectorRegistryVO;
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
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CollectorRegistryServiceImpl implements CollectorRegistryService {

    private final CollectorRegistryMapper collectorRegistryMapper;
    private final TenantAppContext tenantAppContext;

    // ==================== 注册/心跳/下线（供 CollectorRegistrar 调用） ====================

    @Transactional(rollbackFor = Exception.class)
    public void register(RegisterDTO dto) {
        // 先按 collectorId 查找（当前格式）
        CollectorRegistryPO existing = findById(dto.getExecutorId());
        if (existing != null) {
            updateExisting(existing, dto);
            return;
        }
        // 再按 host:port 查找（兼容旧格式 collectorId 迁移）
        List<CollectorRegistryPO> byAddress = collectorRegistryMapper.selectList(
                new LambdaQueryWrapper<CollectorRegistryPO>()
                        .eq(CollectorRegistryPO::getCollectorHost, dto.getHost())
                        .eq(CollectorRegistryPO::getCollectorPort, dto.getPort())
        );
        if (!byAddress.isEmpty()) {
            // 复用第一个匹配记录，删除其余同地址的旧记录，更新 collectorId 为新格式
            CollectorRegistryPO primary = byAddress.get(0);
            for (int i = 1; i < byAddress.size(); i++) {
                collectorRegistryMapper.deleteById(byAddress.get(i).getId());
            }
            primary.setCollectorId(dto.getExecutorId());
            updateExisting(primary, dto);
            log.info("采集器重新注册（兼容旧格式）collectorId={} host={}:{}", dto.getExecutorId(), dto.getHost(), dto.getPort());
            return;
        }
        // 全新注册
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

    private void updateExisting(CollectorRegistryPO po, RegisterDTO dto) {
        po.setCollectorHost(dto.getHost());
        po.setCollectorPort(dto.getPort());
        po.setStatus(RegistryConstants.STATUS_ONLINE);
        po.setLastHeartbeat(LocalDateTime.now());
        if (dto.getAppName() != null) po.setAppName(dto.getAppName());
        if (dto.getAppCode() != null) po.setAppCode(dto.getAppCode());
        collectorRegistryMapper.updateById(po);
        log.info("采集器重新注册 collectorId={} host={}:{}", dto.getExecutorId(), dto.getHost(), dto.getPort());
    }

    @Transactional(rollbackFor = Exception.class)
    public void heartbeat(HeartbeatDTO dto) {
        CollectorRegistryPO po = findById(dto.getExecutorId());
        if (po == null) {
            log.warn("心跳来自未注册采集器 collectorId={}", dto.getExecutorId());
            throw new BizException(ErrorCode.EXECUTOR_NOT_FOUND);
        }
        po.setStatus(RegistryConstants.STATUS_ONLINE);
        po.setLastHeartbeat(LocalDateTime.now());
        collectorRegistryMapper.updateById(po);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deregister(String collectorId) {
        CollectorRegistryPO po = findById(collectorId);
        if (po == null) return;
        po.setStatus(RegistryConstants.STATUS_OFFLINE);
        po.setLastHeartbeat(LocalDateTime.now());
        collectorRegistryMapper.updateById(po);
        log.info("采集器主动下线 collectorId={}", collectorId);
    }

    // ==================== Admin UI 查询/管理 ====================

    @Override
    public CollectorRegistryVO getByCollectorId(String collectorId) {
        CollectorRegistryPO po = findById(collectorId);
        if (po == null) throw new BizException(ErrorCode.EXECUTOR_NOT_FOUND);
        return enrichVO(po);
    }

    @Override
    public List<CollectorRegistryVO> listAllOnline() {
        List<CollectorRegistryPO> list = collectorRegistryMapper.selectList(
                new LambdaQueryWrapper<CollectorRegistryPO>()
                        .eq(CollectorRegistryPO::getStatus, RegistryConstants.STATUS_ONLINE)
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
                .status(po.getStatus())
                .lastHeartbeat(po.getLastHeartbeat())
                .updatedBy(po.getUpdatedBy())
                .createdAt(po.getCreatedAt())
                .updatedAt(po.getUpdatedAt())
                .build();
    }

    private CollectorRegistryVO enrichVO(CollectorRegistryPO po) {
        return toVO(po, po.getAppCode(), po.getAppName());
    }
}
