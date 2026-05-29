package com.zestflow.admin.schedule;

import com.zestflow.admin.model.entity.ExecutorRegistryPO;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 哈希策略 — 按链编码 hash 分配到固定执行器，保证同一链始终命中同一台
 */
@Component
public class HashStrategy implements RouteStrategy {

    @Override
    public String name() {
        return "hash";
    }

    @Override
    public ExecutorRegistryPO select(List<ExecutorRegistryPO> executors, String chainCode) {
        if (executors.isEmpty()) return null;
        int idx = Math.abs(chainCode.hashCode()) % executors.size();
        return executors.get(idx);
    }
}
