package com.zestflow.executor.engine;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * 链实例管理器
 * <p>
 * 维护所有正在运行的链实例，支持查询和终止操作。
 */
@Slf4j
public class ChainInstanceManager {

    private final CopyOnWriteArrayList<ChainInstance> instances = new CopyOnWriteArrayList<>();

    /**
     * 注册运行实例
     */
    public void register(ChainInstance instance) {
        instances.add(instance);
        log.debug("注册链执行实例 instanceId={} chainCode={}", instance.getInstanceId(), instance.getChainCode());
    }

    /**
     * 移除运行实例
     */
    public void unregister(String instanceId) {
        instances.removeIf(inst -> inst.getInstanceId().equals(instanceId));
    }

    /**
     * 根据实例 ID 获取
     */
    public ChainInstance get(String instanceId) {
        return instances.stream()
                .filter(inst -> inst.getInstanceId().equals(instanceId))
                .findFirst()
                .orElse(null);
    }

    /**
     * 获取指定链的所有运行实例，chainCode 为 null 时返回全部
     */
    public List<ChainInstance> listByChainCode(String chainCode) {
        if (chainCode == null) {
            return new ArrayList<>(instances);
        }
        return instances.stream()
                .filter(inst -> inst.getChainCode().equals(chainCode))
                .collect(Collectors.toList());
    }

    /**
     * 终止指定实例
     */
    public boolean stop(String instanceId) {
        ChainInstance instance = get(instanceId);
        if (instance != null) {
            instance.markStopped();
            log.info("终止链执行实例 instanceId={} chainCode={}", instanceId, instance.getChainCode());
            return true;
        }
        return false;
    }

    /**
     * 终止某链的所有实例
     */
    public int stopByChain(String chainCode) {
        List<ChainInstance> running = listByChainCode(chainCode);
        for (ChainInstance inst : running) {
            inst.markStopped();
        }
        log.info("终止链所有实例 chainCode={} count={}", chainCode, running.size());
        return running.size();
    }

    /**
     * 获取所有运行实例数量
     */
    public int count() {
        return instances.size();
    }

    /**
     * 清理已完成的实例（由外部定期调用）
     */
    public void cleanupCompleted() {
        long now = System.currentTimeMillis();
        instances.removeIf(inst -> {
            // 保留最近 5 分钟内的已完成实例用于查询，其余清理
            boolean expired = (now - inst.getStartTime()) > 300_000L
                    && !inst.getStateMachine().isRunning();
            if (expired) {
                log.debug("清理过期实例 instanceId={} chainCode={}", inst.getInstanceId(), inst.getChainCode());
            }
            return expired;
        });
    }
}
