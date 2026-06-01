package com.zestflow.collector.jdbc.service;

import com.zestflow.collector.jdbc.entity.ChainGraphSnapshotPO;
import com.zestflow.collector.jdbc.mapper.ChainEventMapper;
import com.zestflow.collector.jdbc.mapper.ChainGraphSnapshotMapper;
import com.zestflow.common.model.dto.ChainSnapshotDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 链图数据快照服务
 */
@Slf4j
@RequiredArgsConstructor
public class ChainGraphSnapshotService {

    private final ChainGraphSnapshotMapper snapshotMapper;
    private final ChainEventMapper chainEventMapper;

    /**
     * 同步快照：废弃未被引用的旧版本 → 插入新版本
     *
     * @param chainCode 链编码
     * @param graphData 图数据 JSON
     * @param appCode   应用编码
     * @param tenantId  租户ID
     * @param createdBy 操作人
     * @return 生成的版本号
     */
    public int syncSnapshot(String chainCode, String graphData, String appCode, Long tenantId, String createdBy) {
        // 锁定该链所有快照行，防并发
        Integer maxVer = snapshotMapper.selectMaxVersionForUpdate(chainCode, tenantId);
        log.info("同步快照 chainCode={} tenantId={} 当前最大版本={}", chainCode, tenantId, maxVer);

        // 废弃未被引用的旧版本（有执行记录引用的保留）
        snapshotMapper.deprecateUnreferenced(chainCode, tenantId);

        // 插入新版本
        int newVersion = (maxVer != null ? maxVer : 0) + 1;
        LocalDateTime now = LocalDateTime.now();
        ChainGraphSnapshotPO po = ChainGraphSnapshotPO.builder()
                .chainCode(chainCode)
                .version(newVersion)
                .graphData(graphData)
                .status(1)
                .tenantId(tenantId)
                .appCode(appCode)
                .createdBy(createdBy)
                .createdAt(now)
                .updatedAt(now)
                .build();
        snapshotMapper.insert(po);
        log.info("快照已保存 chainCode={} version={} tenantId={}", chainCode, newVersion, tenantId);
        return newVersion;
    }

    /**
     * 查询执行时生效的快照：取 created_at 不超过执行时间戳的最新生效版本
     *
     * @param chainCode     链编码
     * @param timestamp 执行时间戳（毫秒）
     * @return 快照 DTO，查不到返回 null
     */
    @SuppressWarnings("deprecation")
    public ChainSnapshotDTO findSnapshotAt(String chainCode, long timestamp, Long tenantId) {
        LocalDateTime execTime = LocalDateTime.ofEpochSecond(
                timestamp / 1000, (int) ((timestamp % 1000) * 1_000_000),
                ZoneId.systemDefault().getRules().getOffset(java.time.Instant.now()));
        ChainGraphSnapshotPO po = snapshotMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ChainGraphSnapshotPO>()
                        .eq(ChainGraphSnapshotPO::getChainCode, chainCode)
                        .eq(ChainGraphSnapshotPO::getStatus, 1)
                        .eq(tenantId != null, ChainGraphSnapshotPO::getTenantId, tenantId)
                        .le(ChainGraphSnapshotPO::getCreatedAt, execTime)
                        .orderByDesc(ChainGraphSnapshotPO::getVersion)
                        .last("LIMIT 1"));
        if (po == null) {
            // 降级：取该链最新的生效版本（可能比执行时间稍晚，聊胜于无）
            po = snapshotMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ChainGraphSnapshotPO>()
                            .eq(ChainGraphSnapshotPO::getChainCode, chainCode)
                            .eq(ChainGraphSnapshotPO::getStatus, 1)
                            .eq(tenantId != null, ChainGraphSnapshotPO::getTenantId, tenantId)
                            .orderByDesc(ChainGraphSnapshotPO::getVersion)
                            .last("LIMIT 1"));
        }
        if (po == null) return null;
        return toDTO(po);
    }

    private static ChainSnapshotDTO toDTO(ChainGraphSnapshotPO po) {
        return ChainSnapshotDTO.builder()
                .chainCode(po.getChainCode())
                .version(po.getVersion())
                .graphData(po.getGraphData())
                .status(po.getStatus())
                .tenantId(po.getTenantId())
                .appCode(po.getAppCode())
                .createdBy(po.getCreatedBy())
                .createdAt(po.getCreatedAt() != null ? po.getCreatedAt().toString() : null)
                .build();
    }
}
