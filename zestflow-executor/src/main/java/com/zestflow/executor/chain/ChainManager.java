package com.zestflow.executor.chain;

import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.StampedLock;

/**
 * 链路管理器（双缓冲热更新）
 * <p>
 * 使用 {@link StampedLock} 实现高性能读多写少的并发访问：
 * <ul>
 *   <li>读取（高频 >99.9%）：tryOptimisticRead 完全无锁</li>
 *   <li>更新（低频热更新）：writeLock 独占写锁后原子替换 Map 引用</li>
 * </ul>
 * 对标 Nacos 配置中心的双缓冲模型。
 */
@Slf4j
public class ChainManager {

    /** 活跃链定义（当前正在服务请求） */
    private volatile Map<String, ChainDefinition> active = Map.of();

    /** 读写锁（乐观读，适用于读多写少场景） */
    private final StampedLock lock = new StampedLock();

    /**
     * 获取链定义（乐观读，完全无锁）
     */
    public ChainDefinition get(String code) {
        // 乐观读：验证通过则完全无锁
        long stamp = lock.tryOptimisticRead();
        Map<String, ChainDefinition> current = active;
        ChainDefinition def = current.get(code);

        // 验证乐观读是否被写操作打断
        if (!lock.validate(stamp)) {
            // 降级为悲观读锁
            stamp = lock.readLock();
            try {
                def = active.get(code);
            } finally {
                lock.unlockRead(stamp);
            }
        }
        return def;
    }

    /**
     * 批量获取链定义
     */
    public Map<String, ChainDefinition> getBatch(Collection<String> codes) {
        long stamp = lock.tryOptimisticRead();
        Map<String, ChainDefinition> current = active;
        Map<String, ChainDefinition> result = new LinkedHashMap<>();
        for (String code : codes) {
            ChainDefinition def = current.get(code);
            if (def != null) {
                result.put(code, def);
            }
        }

        if (!lock.validate(stamp)) {
            stamp = lock.readLock();
            try {
                result.clear();
                for (String code : codes) {
                    ChainDefinition def = active.get(code);
                    if (def != null) {
                        result.put(code, def);
                    }
                }
            } finally {
                lock.unlockRead(stamp);
            }
        }
        return result;
    }

    /**
     * 加载/替换全部链定义（热更新入口）
     * <p>
     * 先在校验区验证所有链定义的有效性，再获取写锁进行原子替换。
     * 验证在写锁外进行，减少持锁时间。
     */
    public void reload(Collection<ChainDefinition> chainDefinitions) {
        // 写锁外校验，减少持锁时间
        validateAll(chainDefinitions);

        Map<String, ChainDefinition> newActive = new ConcurrentHashMap<>();
        for (ChainDefinition def : chainDefinitions) {
            newActive.put(def.getCode(), def);
        }

        long stamp = lock.writeLock();
        try {
            active = newActive;
            log.info("链定义热更新完成，共加载 {} 条链", newActive.size());
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    /**
     * 加载单个链定义
     */
    public void load(ChainDefinition definition) {
        Objects.requireNonNull(definition, "链定义不能为空");
        Objects.requireNonNull(definition.getCode(), "链编码不能为空");

        long stamp = lock.writeLock();
        try {
            Map<String, ChainDefinition> newActive = new ConcurrentHashMap<>(active);
            newActive.put(definition.getCode(), definition);
            active = newActive;
            log.info("链加载完成 code={}", definition.getCode());
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    /**
     * 卸载链定义
     */
    public void unload(String code) {
        long stamp = lock.writeLock();
        try {
            if (!active.containsKey(code)) {
                log.warn("链不存在，无法卸载 code={}", code);
                return;
            }
            Map<String, ChainDefinition> newActive = new ConcurrentHashMap<>(active);
            newActive.remove(code);
            active = newActive;
            log.info("链已卸载 code={}", code);
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    /**
     * 获取所有活跃链编码
     */
    public Set<String> getActiveCodes() {
        long stamp = lock.tryOptimisticRead();
        Set<String> codes = active.keySet();

        if (!lock.validate(stamp)) {
            stamp = lock.readLock();
            try {
                codes = active.keySet();
            } finally {
                lock.unlockRead(stamp);
            }
        }
        return Collections.unmodifiableSet(codes);
    }

    /**
     * 获取所有活跃链定义（只读快照）
     */
    public Map<String, ChainDefinition> getActiveChains() {
        long stamp = lock.tryOptimisticRead();
        Map<String, ChainDefinition> snapshot = Map.copyOf(active);

        if (!lock.validate(stamp)) {
            stamp = lock.readLock();
            try {
                snapshot = Map.copyOf(active);
            } finally {
                lock.unlockRead(stamp);
            }
        }
        return snapshot;
    }

    /**
     * 获取活跃链数量
     */
    public int activeCount() {
        long stamp = lock.tryOptimisticRead();
        int size = active.size();

        if (!lock.validate(stamp)) {
            stamp = lock.readLock();
            try {
                size = active.size();
            } finally {
                lock.unlockRead(stamp);
            }
        }
        return size;
    }

    /**
     * 判断链是否存在且已加载
     */
    public boolean contains(String code) {
        long stamp = lock.tryOptimisticRead();
        boolean exists = active.containsKey(code);

        if (!lock.validate(stamp)) {
            stamp = lock.readLock();
            try {
                exists = active.containsKey(code);
            } finally {
                lock.unlockRead(stamp);
            }
        }
        return exists;
    }

    /**
     * 写锁在校验区内校验定义合法性
     */
    private void validateAll(Collection<ChainDefinition> definitions) {
        for (ChainDefinition def : definitions) {
            Objects.requireNonNull(def, "链定义不能为空");
            Objects.requireNonNull(def.getCode(), "链编码不能为空");
            if (def.nodeCount() == 0) {
                log.warn("链定义没有节点 code={}", def.getCode());
            }
        }
    }
}
