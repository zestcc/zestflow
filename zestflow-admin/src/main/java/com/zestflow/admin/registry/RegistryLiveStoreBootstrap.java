package com.zestflow.admin.registry;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zestflow.admin.model.entity.CollectorRegistryPO;
import com.zestflow.admin.model.entity.ExecutorRegistryPO;
import com.zestflow.admin.repository.CollectorRegistryMapper;
import com.zestflow.admin.repository.ExecutorRegistryMapper;
import com.zestflow.common.constant.RegistryConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.List;

/**
 * Admin 启动时从 DB 预热内存/Redis 存活表 — Demo 无需因 Admin 重启而重新 register。
 */
@Slf4j
@Component
@Order(0)
@RequiredArgsConstructor
public class RegistryLiveStoreBootstrap implements ApplicationRunner {

    private final RegistryLiveStore liveStore;
    private final ExecutorRegistryMapper executorRegistryMapper;
    private final CollectorRegistryMapper collectorRegistryMapper;

    @Override
    public void run(ApplicationArguments args) {
        long now = System.currentTimeMillis();
        int executors = warmExecutors(now);
        int collectors = warmCollectors(now);
        if (executors > 0 || collectors > 0) {
            log.info("注册存活表预热完成 executors={} collectors={}", executors, collectors);
        }
    }

    private int warmExecutors(long now) {
        List<ExecutorRegistryPO> list = executorRegistryMapper.selectList(
                new LambdaQueryWrapper<ExecutorRegistryPO>()
                        .in(ExecutorRegistryPO::getStatus,
                                RegistryConstants.STATUS_ONLINE,
                                RegistryConstants.STATUS_ABNORMAL));
        for (ExecutorRegistryPO po : list) {
            liveStore.seedExecutor(po.getExecutorId(), toEpoch(po, now));
        }
        return list.size();
    }

    private int warmCollectors(long now) {
        List<CollectorRegistryPO> list = collectorRegistryMapper.selectList(
                new LambdaQueryWrapper<CollectorRegistryPO>()
                        .in(CollectorRegistryPO::getStatus,
                                RegistryConstants.STATUS_ONLINE,
                                RegistryConstants.STATUS_ABNORMAL));
        for (CollectorRegistryPO po : list) {
            liveStore.seedCollector(po.getCollectorId(), toEpoch(po, now));
        }
        return list.size();
    }

    private static long toEpoch(ExecutorRegistryPO po, long fallback) {
        if (po.getLastHeartbeat() == null) {
            return fallback;
        }
        return po.getLastHeartbeat().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private static long toEpoch(CollectorRegistryPO po, long fallback) {
        if (po.getLastHeartbeat() == null) {
            return fallback;
        }
        return po.getLastHeartbeat().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
